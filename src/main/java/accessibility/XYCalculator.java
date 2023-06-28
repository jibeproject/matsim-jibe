package accessibility;

import accessibility.decay.DecayFunction;
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
import routing.graph.LeastCostPathTree3;
import routing.graph.SpeedyGraph;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

// todo: Currently calculates 2 least cost path trees for every point. Need to optimise so that points along the same link are all done at once.

public class XYCalculator {
    private final static Logger log = Logger.getLogger(XYCalculator.class);
    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public static void calculate(Network routingNetwork, SimpleFeatureCollection collection,
                                 Map<String, IdSet<Node>> destinationNodes, Map<String, Double> destinationWeights,
                                 TravelTime travelTime, TravelDisutility travelDisutility,
                                 Vehicle vehicle, DecayFunction decayFunction) throws IOException {

        // Create set of cells
        Set<SimpleFeature> origins = new HashSet<>();
        SimpleFeatureIterator iterator = collection.features();
        while(iterator.hasNext()) {
            origins.add(iterator.next());
        }
        iterator.close();
        log.info("Calculating accessibility for " + origins.size() + " polygons.");


        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        SpeedyGraph routingGraph = new SpeedyGraph(routingNetwork,travelTime,travelDisutility,PERSON,vehicle);

        // Precalculate marginal travel times
        Map<Id<Link>,Double> marginalTravelTimes = NetworkUtils2.precalculateLinkMarginalDisutilities(routingNetwork, new OnlyTimeDependentTravelDisutility(travelTime), 0.,PERSON, vehicle);

        // Precalculate marginal disutilities
        Map<Id<Link>,Double> marginalDisutilities = NetworkUtils2.precalculateLinkMarginalDisutilities(routingNetwork, travelDisutility, 0.,PERSON, vehicle);

        // Create concurrent linked queue for multithreading
        ConcurrentLinkedQueue<SimpleFeature> originsQueue = new ConcurrentLinkedQueue<>(origins);

        // Prepare and start threads
        Counter counter = new Counter("Calculating accessibility for point ", " / " + origins.size());
        Thread[] threads = new Thread[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            XYWorker worker = new XYWorker(originsQueue, destinationWeights,destinationNodes,
                    routingNetwork,routingGraph, decayFunction,marginalTravelTimes,marginalDisutilities,counter);
            threads[i] = new Thread(worker, "GridAccessibility-" + i);
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
        double min = origins.stream().mapToDouble(c -> (double) c.getAttribute("accessibility")).min().orElseThrow();
        double max = origins.stream().mapToDouble(c -> (double) c.getAttribute("accessibility")).max().orElseThrow();
        double diff = max - min;
        for (SimpleFeature origin : origins) {
            double accessibility = (double) origin.getAttribute("accessibility");
            origin.setAttribute("normalised",(accessibility-min) / diff);
        }
    }

    private static class XYWorker implements Runnable {

        private final ConcurrentLinkedQueue<SimpleFeature> pointsQueue;
        private final Map<String, Double> destinationWeights;
        private final Map<String, IdSet<Node>> destinationNodes;
        private final Map<Id<Link>,Double> marginalTravelTimes;
        private final Map<Id<Link>,Double> marginalDisutilities;
        private final Network network;
        private final SpeedyGraph graph;
        private final Counter counter;
        private final DecayFunction decayFunction;

        XYWorker(ConcurrentLinkedQueue<SimpleFeature> pointsQueue,
                 Map<String, Double> destinationWeights, Map<String, IdSet<Node>> destinationNodes,
                 Network network, SpeedyGraph graph, DecayFunction decayFunction,
                 Map<Id<Link>,Double> marginalTravelTimes, Map<Id<Link>,Double> marginalDisutilities,
                 Counter counter) {
            this.pointsQueue = pointsQueue;
            this.destinationWeights = destinationWeights;
            this.destinationNodes = destinationNodes;
            this.network = network;
            this.graph = graph;
            this.decayFunction = decayFunction;
            this.marginalTravelTimes = marginalTravelTimes;
            this.marginalDisutilities = marginalDisutilities;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree3 lcpTree = new LeastCostPathTree3(this.graph);
            LeastCostPathTree3.StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

            while (true) {
                SimpleFeature feature = this.pointsQueue.poll();
                if (feature == null) {
                    return;
                }
                this.counter.incCounter();

                Point point = (Point) feature.getDefaultGeometry();
                Coord coord = new Coord(point.getX(),point.getY());

                Link link = NetworkUtils.getNearestLinkExactly(network, coord);
                Id<Link> linkId = link.getId();
                double connectorMarginalCost = marginalDisutilities.get(linkId);
                double connectorMarginalTime = marginalTravelTimes.get(linkId);

                Id<Node> finalConnectorNodeId = null;
                Double finalConnectorLength = null;
                Double finalConnectorCost = null;
                Double finalConnectorTime = null;
                double finalAccessibility = 0.;

                IdSet<Node> nodeIds = new IdSet<>(Node.class);
                nodeIds.add(link.getFromNode().getId());
                nodeIds.add(link.getToNode().getId());

                for (Id<Node> nodeId : nodeIds) {
                    lcpTree.calculate(nodeId.index(),0.,stopCriterion,true);
                    double accessibility = 0.;
                    double connectorLength = Math.max(0.,CoordUtils.calcProjectedEuclideanDistance(coord,network.getNodes().get(nodeId).getCoord()));
                    double connectorCost = connectorMarginalCost * connectorLength;
                    double connectorTime = connectorMarginalTime * connectorLength;

                    for (Map.Entry<String, Double> destination : this.destinationWeights.entrySet()) {
                        double cost = Double.MAX_VALUE;
                        for (Id<Node> toNodeId : this.destinationNodes.get(destination.getKey())) {
                            int toNodeIndex = toNodeId.index();
                            double nodeDist = lcpTree.getDistance(toNodeIndex) + connectorLength;
                            double nodeTime = lcpTree.getTime(toNodeIndex).orElse(Double.POSITIVE_INFINITY) + connectorTime;
                            if(!decayFunction.withinCutoff(nodeDist,nodeTime)) {
                                continue;
                            }
                            double nodeCost = lcpTree.getCost(toNodeIndex) + connectorCost;
                            if (nodeCost < cost) {
                                cost = nodeCost;
                            }
                        }
                        if(cost != Double.MAX_VALUE) {
                            double wt = destination.getValue();
                            accessibility += decayFunction.getDecay(cost) * wt;
                        }
                    }
                    if (accessibility > finalAccessibility) {
                        finalAccessibility = accessibility;
                        finalConnectorNodeId = nodeId;
                        finalConnectorLength = connectorLength;
                        finalConnectorCost = connectorCost;
                        finalConnectorTime = connectorTime;
                    }
                }

                feature.setAttribute("accessibility", finalAccessibility);
                feature.setAttribute("connector_node", finalConnectorNodeId != null ? Integer.parseInt(finalConnectorNodeId.toString()) : null);
                feature.setAttribute("connector_dist", finalConnectorLength);
                feature.setAttribute("connector_cost", finalConnectorCost);
                feature.setAttribute("connector_time", finalConnectorTime);
            }
        }
    }
}
