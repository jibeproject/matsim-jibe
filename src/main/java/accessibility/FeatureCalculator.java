package accessibility;

import accessibility.decay.DecayFunction;
import gis.GisUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import resources.Properties;
import resources.Resources;
import routing.graph.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FeatureCalculator {
    private final static Logger log = Logger.getLogger(FeatureCalculator.class);
    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public static void calculate(Network routingNetwork, SimpleFeatureCollection collection,
                                 List<LocationData> endDataList,
                                 Map<Id<Node>,double[]> nodeResults, int polygonRadius,
                                 Boolean fwd, TravelTime travelTime, TravelDisutility travelDisutility,
                                 Vehicle vehicle, DecayFunction decayFunction) {

        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        SpeedyGraph routingGraph = new SpeedyGraph(routingNetwork,travelTime,travelDisutility,PERSON,vehicle);

        // Create set of cells
        Set<SimpleFeature> features = new HashSet<>();
        SimpleFeatureIterator iterator = collection.features();
        while(iterator.hasNext()) {
            features.add(iterator.next());
        }
        iterator.close();
        log.info("Calculating accessibility for " + features.size() + " polygons.");

        // Assign nodes to polygon features (if nodeResults == null this means they are point features)
        Map<SimpleFeature, IdSet<Node>> nodesPerZone = null;
        if (nodeResults != null) {
            nodesPerZone = GisUtils.assignNodesToZones(features,nodeResults.keySet(),routingNetwork);
        }

        // Precalculate marginal travel times
        Map<Id<Link>,Double> marginalTravelTimes = NetworkUtils2.precalculateLinkMarginalDisutilities(routingNetwork, new OnlyTimeDependentTravelDisutility(travelTime), 0.,PERSON, vehicle);

        // Precalculate marginal disutilities
        Map<Id<Link>,Double> marginalDisutilities = NetworkUtils2.precalculateLinkMarginalDisutilities(routingNetwork, travelDisutility, 0.,PERSON, vehicle);

        // Create concurrent linked queue for multithreading
        ConcurrentLinkedQueue<SimpleFeature> featuresQueue = new ConcurrentLinkedQueue<>(features);

        // Prepare and start threads
        Counter counter = new Counter("Calculating accessibility feature ", " / " + features.size());
        Thread[] threads = new Thread[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            FeatureWorker worker = new FeatureWorker(featuresQueue, polygonRadius, nodeResults,endDataList,fwd,nodesPerZone,
                    routingNetwork,routingGraph, decayFunction,marginalTravelTimes,marginalDisutilities,counter);
            threads[i] = new Thread(worker, "PolygonAccessibility-" + i);
            threads[i].start();
        }

        // wait until all threads have finished
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // normalise results
        for(LocationData endData : endDataList) {
            String attributeName = "accessibility_" + endData.getDescription();
            double min = features.stream().mapToDouble(c -> (double) c.getAttribute(attributeName)).min().orElseThrow();
            double max = features.stream().mapToDouble(c -> (double) c.getAttribute(attributeName)).max().orElseThrow();
            double diff = max - min;
            for (SimpleFeature feature : features) {
                double accessibility = (double) feature.getAttribute(attributeName);
                feature.setAttribute("normalised_" + endData.getDescription(),(accessibility-min) / diff);
            }
        }
    }

    private static class FeatureWorker implements Runnable {

        private final ConcurrentLinkedQueue<SimpleFeature> features;
        private final int zoneRadius;
        private final Map<Id<Node>, double[]> nodeResults;
        private final List<LocationData> endDataList;
        private final Boolean fwd;
        private final Map<Id<Link>,Double> marginalTravelTimes;
        private final Map<Id<Link>,Double> marginalDisutilities;
        private final Map<SimpleFeature, IdSet<Node>> nodesInPolygons;
        private final Network network;
        private final SpeedyGraph graph;
        private final Counter counter;
        private final DecayFunction decayFunction;

        FeatureWorker(ConcurrentLinkedQueue<SimpleFeature> features, int zoneRadius, Map<Id<Node>, double[]> nodeResults,
                      List<LocationData> endDataList, Boolean fwd,
                      Map<SimpleFeature, IdSet<Node>> nodesInPolygons, Network network,
                      SpeedyGraph graph, DecayFunction decayFunction,
                      Map<Id<Link>,Double> marginalTravelTimes, Map<Id<Link>,Double> marginalDisutilities,
                      Counter counter) {
            this.features = features;
            this.zoneRadius = zoneRadius;
            this.nodeResults = nodeResults;
            this.endDataList = endDataList;
            this.fwd = fwd;
            this.nodesInPolygons = nodesInPolygons;
            this.network = network;
            this.graph = graph;
            this.decayFunction = decayFunction;
            this.marginalTravelTimes = marginalTravelTimes;
            this.marginalDisutilities = marginalDisutilities;
            this.counter = counter;
        }

        public void run() {
            PathTree lcpTree;
            if(fwd != null) {
                log.info("Initialising 1-way least cost path tree in " + (fwd ? " FORWARD " : " REVERSE ") + " direction...");
                lcpTree = new LcpTree1Way(this.graph,fwd);
            } else {
                log.info("Initialising 2-way least cost path tree...");
                lcpTree = new LcpTree2Way(this.graph);
            }

            StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

            while (true) {
                SimpleFeature feature = this.features.poll();
                if (feature == null) {
                    return;
                }
                this.counter.incCounter();

                // POLYGON Calculation
                if (nodeResults != null) {
                    IdSet<Node> nodesInside = nodesInPolygons.get(feature);

                    // Store number of nodes in each cell as an attribute
                    int nodesWithin = nodesInside != null ? nodesInside.size() : 0;
                    feature.setAttribute("nodes_within",nodesWithin);

                    // If no nodes fall inside zone, then use to & from node of nearest link
                    if(nodesWithin > 0) {
                        for(int i = 0 ; i < endDataList.size() ; i++) {
                            int finalI = i;
                            feature.setAttribute("accessibility_" + endDataList.get(finalI).getDescription(),
                                    nodesInside.stream().mapToDouble(n -> nodeResults.get(n)[finalI]).average().orElseThrow());
                        }
                    } else {
                        Coord centroid = new Coord((double) feature.getAttribute("centroid_x"), (double) feature.getAttribute("centroid_y"));
                        calculateForPoint(feature, centroid, lcpTree, stopCriterion);
                    }
                } else {
                    Point point = (Point) feature.getDefaultGeometry();
                    Coord coord = new Coord(point.getX(), point.getY());
                    calculateForPoint(feature, coord, lcpTree, stopCriterion);
                }
            }
        }
        void calculateForPoint(SimpleFeature feature, Coord coord, PathTree lcpTree, StopCriterion stopCriterion) {
            Link link = NetworkUtils.getNearestLinkExactly(network, coord);
            Id<Link> linkId = link.getId();
            double connectorMarginalCost = marginalDisutilities.get(linkId);
            double connectorMarginalTime = marginalTravelTimes.get(linkId);

            Node nodeA = link.getFromNode();
            Node nodeB = link.getToNode();

            double connectorLengthA = Math.max(0.,CoordUtils.calcProjectedEuclideanDistance(coord,nodeA.getCoord()) - zoneRadius);
            double connectorLengthB = Math.max(0.,CoordUtils.calcProjectedEuclideanDistance(coord,nodeB.getCoord()) - zoneRadius);

            double costA = connectorMarginalCost * connectorLengthA;
            double costB = connectorMarginalCost * connectorLengthB;

            double timeA = connectorMarginalTime * connectorLengthA;
            double timeB = connectorMarginalTime * connectorLengthB;

            feature.setAttribute("nodeA",nodeA.getId().toString());
            feature.setAttribute("costA",costA);
            feature.setAttribute("nodeB",nodeB.getId().toString());
            feature.setAttribute("costB",costB);

            lcpTree.calculate(
                    nodeA.getId().index(),costA,timeA,connectorLengthA,
                    nodeB.getId().index(),costB,timeB,connectorLengthB,
                    0.,stopCriterion);

            for(LocationData endData: endDataList) {
                Map<String, IdSet<Node>> endNodes = endData.getNodes();
                Map<String, Double> endWeights = endData.getWeights();

                double accessibility = 0.;
                for (Map.Entry<String, Double> endWeight : endWeights.entrySet()) {
                    double cost = Double.MAX_VALUE;
                    for (Id<Node> toNodeId : endNodes.get(endWeight.getKey())) {
                        int toNodeIndex = toNodeId.index();
                        double nodeDist = lcpTree.getDistance(toNodeIndex);
                        double nodeTime = lcpTree.getTime(toNodeIndex).orElse(Double.POSITIVE_INFINITY);
                        if(decayFunction.beyondCutoff(nodeDist, nodeTime)) {
                            continue;
                        }
                        double nodeCost = lcpTree.getCost(toNodeIndex);
                        if (nodeCost < cost) {
                            cost = nodeCost;
                        }
                    }
                    if(cost != Double.MAX_VALUE) {
                        accessibility += decayFunction.getDecay(cost) * endWeight.getValue();
                    }
                }
                feature.setAttribute("accessibility_" + endData.getDescription(),accessibility);
            }
        }
    }
}
