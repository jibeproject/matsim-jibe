package accessibility;

import accessibility.decay.DecayFunction;
import accessibility.decay.Hansen;
import gis.GisUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GridCalculator {
    private final static Logger log = Logger.getLogger(GridCalculator.class);
    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));
    private final static double SIDE_LENGTH_METERS = RunGridAnalysis.SIDE_LENGTH_METERS; // todo: capture this automatically

    public static void calculate(IdMap<Node,Double> nodeAccessibilities, SimpleFeatureCollection collection,
                                 Network network, DecayFunction decayFunction, TravelDisutility disutility, Vehicle vehicle, int numberOfThreads) throws IOException {


        // Check decay function type // todo: implement for other types of decay functions
        if(!(decayFunction instanceof Hansen)) {
            throw new RuntimeException("The Grid calculator currently only works for continuous hansen decay functions!");
        }

        // Create set of cells
        Set<SimpleFeature> cells = new HashSet<>();
        SimpleFeatureIterator iterator = collection.features();
        while(iterator.hasNext()) {
            cells.add(iterator.next());
        }
        iterator.close();
        log.info("Calculating accessibility for " + cells.size() + " polygons.");


        // Assign nodes to grid cells
        ConcurrentHashMap<SimpleFeature, IdSet<Node>> nodesPerZone = new ConcurrentHashMap<>(GisUtils.assignNodesToZones(cells,nodeAccessibilities.keySet(),network));

        // Precalculate marginal disutilities
        ConcurrentHashMap<Link,Double> marginalDisutilities = new ConcurrentHashMap<>(NetworkUtils2.precalculateLinkMarginalDisutilities(network, disutility, 0.,PERSON, vehicle));

        // Convert node results to a concurrent hash map
        ConcurrentHashMap<Id<Node>,Double> nodeResults = new ConcurrentHashMap<>(nodeAccessibilities);

        // Create concurrent linked queue for multithreading
        ConcurrentLinkedQueue<SimpleFeature> cellsQueue = new ConcurrentLinkedQueue<>(cells);

        // Prepare and start threads
        Counter counter = new Counter("Calculating accessibility node ", " / " + cells.size());
        Thread[] threads = new Thread[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            CellWorker worker = new CellWorker(cellsQueue,nodeResults,nodesPerZone,network, decayFunction,marginalDisutilities,counter);
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
        double min = cells.stream().mapToDouble(c -> (double) c.getAttribute("accessibility")).min().orElseThrow();
        double max = cells.stream().mapToDouble(c -> (double) c.getAttribute("accessibility")).max().orElseThrow();
        double diff = max - min;
        for (SimpleFeature cell : cells) {
            double accessibility = (double) cell.getAttribute("accessibility");
            cell.setAttribute("normalised",(accessibility-min) / diff);
        }
    }

    private static class CellWorker implements Runnable {

        private final ConcurrentLinkedQueue<SimpleFeature> cells;
        private final ConcurrentHashMap<Id<Node>, Double> nodeAccessibilities;
        private final ConcurrentHashMap<Link,Double> marginalDisutilities;
        private final ConcurrentHashMap<SimpleFeature, IdSet<Node>> nodesPerCell;
        private final Network network;
        private final Counter counter;
        private final DecayFunction decayFunction;

        CellWorker(ConcurrentLinkedQueue<SimpleFeature> cells, ConcurrentHashMap<Id<Node>, Double> nodeAccessibilities,
                   ConcurrentHashMap<SimpleFeature, IdSet<Node>> nodesPerCell, Network network,
                   DecayFunction decayFunction, ConcurrentHashMap<Link,Double> marginalDisutilities, Counter counter) {
            this.cells = cells;
            this.nodeAccessibilities = nodeAccessibilities;
            this.nodesPerCell = nodesPerCell;
            this.network = network;
            this.decayFunction = decayFunction;
            this.marginalDisutilities = marginalDisutilities;
            this.counter = counter;
        }

        public void run() {
            while (true) {
                SimpleFeature cell = this.cells.poll();
                if (cell == null) {
                    return;
                }
                this.counter.incCounter();

                IdSet<Node> nodesInside = nodesPerCell.get(cell);

                // Store number of nodes in each cell as an attribute
                int nodesWithin = nodesInside != null ? nodesInside.size() : 0;
                cell.setAttribute("nodes_within",nodesWithin);

                // If no nodes fall inside zone, then use to & from node of nearest link
                if(nodesWithin > 0) {
                    cell.setAttribute("accessibility", nodesInside.stream().mapToDouble(nodeAccessibilities::get).average().orElseThrow());
                } else {
                    Coord centroid = new Coord((double) cell.getAttribute("centroid_x"), (double) cell.getAttribute("centroid_y"));
                    Link link = NetworkUtils.getNearestLinkExactly(network, centroid);
                    calculateAndStoreConnectorBasedAccessibility(cell,centroid, link);
                }

            }
        }

        private void calculateAndStoreConnectorBasedAccessibility(SimpleFeature cell, Coord c,Link link) {

            Id<Node> finalConnectorNodeId = null;
            Double finalConnectorLength = null;
            Double finalConnectorMarginalDisutility = null;
            Double finalConnectorDisutility = null;
            Double finalConnectorAdjustment = null;
            double finalAccessibility = Double.MAX_VALUE;

            IdSet<Node> nodeIds = new IdSet<>(Node.class);
            nodeIds.add(link.getFromNode().getId());
            nodeIds.add(link.getToNode().getId());

            for (Id<Node> nodeId : nodeIds) {
                double connectorLength = Math.max(0.,CoordUtils.calcProjectedEuclideanDistance(c,network.getNodes().get(nodeId).getCoord()) - SIDE_LENGTH_METERS);
                double connectorMarginalDisutility = marginalDisutilities.get(link);
                double connectorDisutility = connectorMarginalDisutility * connectorLength;
                double connectorAdjustment = decayFunction.getDecay(connectorDisutility);
                double accessibility = connectorAdjustment * nodeAccessibilities.get(nodeId);
                if (accessibility < finalAccessibility) {
                    finalConnectorNodeId = nodeId;
                    finalConnectorLength = connectorLength;
                    finalConnectorMarginalDisutility = connectorMarginalDisutility;
                    finalConnectorDisutility = connectorDisutility;
                    finalConnectorAdjustment = connectorAdjustment;
                    finalAccessibility = accessibility;
                }

                if (finalAccessibility == Double.MAX_VALUE) {
                    throw new RuntimeException("No result found!"); // todo: remove this
                }

                cell.setAttribute("connector_node", Integer.parseInt(finalConnectorNodeId.toString()));
                cell.setAttribute("connector_dist", finalConnectorLength);
                cell.setAttribute("connector_marg_disutility", finalConnectorMarginalDisutility);
                cell.setAttribute("connector_disutility", finalConnectorDisutility);
                cell.setAttribute("connector_adj", finalConnectorAdjustment);
                cell.setAttribute("accessibility", finalAccessibility);
            }
        }
    }
}
