package trads.calculate;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.misc.Counter;
import resources.Resources;
import routing.graph.DistTree;
import routing.graph.SpeedyGraph;
import trip.Place;
import trip.Trip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

// Calculates all possible routes within certain detour limit

public final class MultiLinkCalculator {

    private MultiLinkCalculator() {
    }

    private final static int MAX_MATRIX_DIM = 25000;

    public static final Logger log = Logger.getLogger(MultiLinkCalculator.class);

    public static Map<Trip,IdMap<Link,Double>> calculate(Collection<Trip> trips, Place origin, Place destination,
                                 Network routingNetwork, Network xy2lNetwork, double distDetour) {

        int numberOfThreads = Resources.instance.getInt(resources.Properties.NUMBER_OF_THREADS);
        SpeedyGraph graph = new SpeedyGraph(routingNetwork,null,null,null,null);

        // prepare calculation
        ConcurrentLinkedQueue<Trip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter("Calculating multi-route node ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        RouteWorker[] workers = new RouteWorker[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            workers[i] = new RouteWorker(odPairsQueue, counter, origin, destination, routingNetwork, xy2lNetwork, graph, distDetour);
            threads[i] = new Thread(workers[i], "Accessibility-" + i);
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

        // Combine results
        Map<Trip,IdMap<Link,Double>> allResults = new HashMap<>();
        for (int i = 0; i < numberOfThreads; i++) {
            allResults.putAll(workers[i].results);
        }

        return allResults;
    }

    private static class RouteWorker implements Runnable {
        private final ConcurrentLinkedQueue<Trip> trips;
        private final Counter counter;
        private final Place origin;
        private final Place destination;
        private final Network routingNetwork;
        private final Network xy2lNetwork;
        private final SpeedyGraph graph;
        private final double detourLimit;
        private final int[] ref;
        private final double[][] matrix;
        private final DistTree origTree;
        private final DistTree destTree;
        private final Map<Trip,IdMap<Link,Double>> results;

        public RouteWorker(ConcurrentLinkedQueue<Trip> trips, Counter counter,
                           Place origin, Place destination,
                           Network routingNetwork, Network xy2lNetwork, SpeedyGraph graph,
                           double distDetour) {
            this.trips = trips;
            this.counter = counter;
            this.origin = origin;
            this.destination = destination;
            this.routingNetwork = routingNetwork;
            this.xy2lNetwork = xy2lNetwork;
            this.graph = graph;
            this.detourLimit = distDetour;
            this.ref = new int[graph.getNodeCount()];
            this.matrix = new double[MAX_MATRIX_DIM+500][MAX_MATRIX_DIM+500];
            this.origTree = new DistTree(graph);
            this.destTree = new DistTree(graph);
            this.results = new HashMap<>(trips.size() / Resources.instance.getInt(resources.Properties.NUMBER_OF_THREADS));
        }

        public void run() {

            while (true) {

                Trip trip = this.trips.poll();

                if (trip == null) {
                    return;
                }
                String tripName = trip.getHouseholdId() + "-" + trip.getPersonId() + "-" + trip.getTripId();

                this.counter.incCounter();

                if (trip.routable(origin, destination)) {

                    IdMap<Link, Double> linkDetours = new IdMap<>(Link.class);
                    Map<Integer, Double> fromNodeIdxDetours = new HashMap<>();
                    Map<Integer, Double> toNodeIdxDetours = new HashMap<>();

                    Coord cOrig = trip.getCoord(origin);
                    Coord cDest = trip.getCoord(destination);
                    Node nOrig = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cOrig).getToNode().getId());
                    Node nDest = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cDest).getToNode().getId());
                    int startNodeIdx = nOrig.getId().index();
                    int endNodeIdx = nDest.getId().index();

                    // Calculate multi tree
                    origTree.calculate(startNodeIdx, endNodeIdx, -1, this.detourLimit, true);
                    destTree.calculate(startNodeIdx, endNodeIdx, -1, this.detourLimit, false);

                    // Results
                    double shortestDist = origTree.getCost(endNodeIdx);
                    assert shortestDist == destTree.getCost(startNodeIdx);

                    // Create matrix
                    // Identify & tree from all candidate nodes
                    int validNodes = updateReference(shortestDist * this.detourLimit);
                    log.info(tripName + ": " + shortestDist + " metres. " + validNodes + " nodes within " + this.detourLimit + " detour.");

                    if (validNodes > MAX_MATRIX_DIM) {
                        log.info(tripName + ": Reducing outer detour limit...");
                        double[] dist = new double[validNodes];
                        for (int i = 0; i < graph.getNodeCount(); i++) {
                            if (ref[i] != -1) {
                                dist[ref[i]] = origTree.getCost(i) + destTree.getCost(i);
                            }
                        }
                        Arrays.sort(dist);
                        validNodes = updateReference(dist[MAX_MATRIX_DIM-1]);
                        log.info(tripName + ": " + shortestDist + " metres. " + validNodes + " nodes within " + dist[MAX_MATRIX_DIM-1] / shortestDist + " detour.");
                    }

                    // Updating shortest distance matrix for region
                    log.info(tripName + ": Updating shortest cost matrix...");
                    updateMatrix(startNodeIdx,endNodeIdx);
                    assert shortestDist == matrix[ref[startNodeIdx]][ref[endNodeIdx]];

                    // Identify & tree from all candidate nodes
                    SpeedyGraph.LinkIterator li = graph.getOutLinkIterator();
                    int c = 0;
                    for (int i = 0; i < graph.getNodeCount(); i++) {
                        if (ref[i] != -1) {
                            li.reset(i);
                            while (li.next()) {
                                Link link = graph.getLink(li.getLinkIndex());
                                int j = li.getToNodeIndex();
                                if (ref[j] != -1) {
                                    double maxDetour = getMaxDetour(i, j, link.getLength(),tripName);
                                    if (maxDetour < this.detourLimit) {
                                        c++;
                                        linkDetours.put(link.getId(), maxDetour);
                                        fromNodeIdxDetours.put(i, maxDetour);
                                        toNodeIdxDetours.put(j, maxDetour);
                                    }
                                }
                            }
                        }
                    }

                    // Loop over again and fill in gaps
                    fillGaps(origTree, linkDetours, fromNodeIdxDetours);
                    fillGaps(destTree, linkDetours, toNodeIdxDetours);

                    // Store results
                    results.put(trip,linkDetours);
                    log.info(tripName + ": " + shortestDist + " metres. " + c + " links.");

                } else {
                    log.info(tripName + ": NOT ROUTABLE!");
                }
            }
        }

        private int updateReference(double maxCost) {
            Arrays.fill(ref, -1);
            int refIdx = 0;
            for (int i = 0; i < graph.getNodeCount(); i++) {
                double origCost = origTree.getCost(i);
                double destCost = destTree.getCost(i);
                if (origCost + destCost <= maxCost) {
                    ref[i] = refIdx;
                    refIdx++;
                }
            }
            return refIdx;
        }

        private void updateMatrix(int startNodeIdx, int endNodeIdx) {
            for(double[] row : matrix) {
                Arrays.fill(row,Double.NaN);
            }
            DistTree iTree = new DistTree(graph);
            for (int i = 0; i < graph.getNodeCount(); i++) {
                if (ref[i] != -1) {
                    iTree.calculate(startNodeIdx, endNodeIdx, i, -1, true);
                    for (int j = 0; j < graph.getNodeCount(); j++) {
                        if (ref[j] != -1) {
                            matrix[ref[i]][ref[j]] = iTree.getCost(j);
                        }
                    }
                }
            }
        }

        private void fillGaps(DistTree tree, IdMap<Link, Double> linkDetours, Map<Integer, Double> nodeIdxDetours) {
            for (Map.Entry<Integer, Double> node : nodeIdxDetours.entrySet()) {

                int currIdx = node.getKey();
                int linkIdx = tree.getComingFromLink(currIdx);
                double detour = node.getValue();
                while (linkIdx != -1) {
                    Id<Link> link = graph.getLink(linkIdx).getId();

                    if (linkDetours.containsKey(link)) {
                        double currDetour = linkDetours.get(link);
                        if (currDetour > detour) {
                            linkDetours.put(link, detour);
                        }
                    } else {
                        linkDetours.put(link, detour);
                    }
                    currIdx = tree.getComingFrom(currIdx);
                    linkIdx = tree.getComingFromLink(currIdx);
                }
            }
        }

        public double getMaxDetour(int idxA, int idxB, double linkCost, String tripName) {

            double totalCost = origTree.getCost(idxA) + linkCost + destTree.getCost(idxB);

            double maxDetour = 1.;

            int currIdxA = idxA;
            while (currIdxA != -1) {
                int refCurrIdxA = ref[currIdxA];
                if(refCurrIdxA != -1) {
                    double distA = origTree.getCost(currIdxA);
                    int currIdxB = idxB;
                    while(currIdxB != -1) {
                        int refCurrIdxB = ref[currIdxB];
                        if(refCurrIdxB != -1) {
                            double distB = destTree.getCost(currIdxB);
                            double pathDist = totalCost - distA - distB;
                            double shortDist = matrix[refCurrIdxA][refCurrIdxB];
                            double detour = pathDist / shortDist;
                            if (detour > this.detourLimit) {
                                return detour;
                            }
                            if (detour > maxDetour) {
                                maxDetour = detour;
                            }
                        } else {
                            log.warn(tripName + ": bad fwd indeces for idx " + idxB);
                            return Double.POSITIVE_INFINITY;
                        }
                        currIdxB = destTree.getComingFrom(currIdxB);
                    }
                } else {
                    log.warn(tripName + ": bad rev indeces for idx " + idxA);
                    return Double.POSITIVE_INFINITY;
                }
                currIdxA = origTree.getComingFrom(currIdxA);
            }
            return maxDetour;
        }
    }
}
