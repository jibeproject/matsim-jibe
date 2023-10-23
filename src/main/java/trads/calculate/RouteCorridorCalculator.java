package trads.calculate;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import resources.Resources;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import routing.graph.SimpleTree;
import routing.graph.SpeedyGraph;
import routing.graph.TreeNode;
import trip.Place;
import trip.Trip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

// Calculates all possible routes within certain detour limit

public final class RouteCorridorCalculator {

    private RouteCorridorCalculator() {
    }

    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public static final Logger log = Logger.getLogger(RouteCorridorCalculator.class);

    public static void calculate(Set<Trip> trips, Place origin, Place destination,
                                 Network routingNetwork, Network xy2lNetwork,
                                 TravelTime travelTime, Vehicle vehicle, double distDetour) {

        int numberOfThreads = Resources.instance.getInt(resources.Properties.NUMBER_OF_THREADS);
        SpeedyGraph graph = new SpeedyGraph(routingNetwork,null,null,null,null);

        // prepare calculation
        ConcurrentLinkedQueue<Trip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter("Calculating multi-route node ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RouteWorker worker = new RouteWorker(odPairsQueue, counter, origin, destination,
                    vehicle, routingNetwork, xy2lNetwork, graph, travelTime, distDetour);
            threads[i] = new Thread(worker, "Accessibility-" + i);
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
    }

    private static class RouteWorker implements Runnable {
        private final ConcurrentLinkedQueue<Trip> trips;
        private final Counter counter;
        private final Vehicle vehicle;
        private final Place origin;
        private final Place destination;
        private final TravelTime travelTime;
        private final Network routingNetwork;
        private final Network xy2lNetwork;
        private final SpeedyGraph graph;
        private final double distDetour;

        private int c1;
        private int c2;
        private int c3;
        private int c4;

        public RouteWorker(ConcurrentLinkedQueue<Trip> trips, Counter counter,
                           Place origin, Place destination, Vehicle vehicle,
                           Network routingNetwork, Network xy2lNetwork, SpeedyGraph graph,
                           TravelTime travelTime, double distDetour) {
            this.trips = trips;
            this.counter = counter;
            this.origin = origin;
            this.destination = destination;
            this.vehicle = vehicle;
            this.routingNetwork = routingNetwork;
            this.xy2lNetwork = xy2lNetwork;
            this.graph = graph;
            this.travelTime = travelTime;
            this.distDetour = distDetour;
        }

        public void run() {

            SimpleTree origTree = new SimpleTree(graph);
            SimpleTree destTree = new SimpleTree(graph);

            while(true) {

                c1 = c2 = c3 = c4 = 0;

                Trip trip = this.trips.poll();
                if(trip == null) {
                    return;
                }

                this.counter.incCounter();

                if(trip.routable(origin, destination)) {
                    String tripName = "TRIP " + trip.getHouseholdId() + "-" + trip.getPersonId() + "-" + trip.getTripId();
                    Coord cOrig = trip.getCoord(origin);
                    Coord cDest = trip.getCoord(destination);
                    Node nOrig = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cOrig).getToNode().getId());
                    Node nDest = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cDest).getToNode().getId());
                    int startNodeIdx = nOrig.getId().index();
                    int endNodeIdx = nDest.getId().index();

                    // Calculate multi tree
                    origTree.calculate(startNodeIdx,endNodeIdx,-1,this.distDetour,true);
                    destTree.calculate(startNodeIdx,endNodeIdx,-1,this.distDetour,false);

                    // Results
                    double shortestDist = origTree.getCost(endNodeIdx);
                    assert shortestDist == destTree.getCost(startNodeIdx);
                    log.info(tripName + ": SHORTEST DISTANCE: " + shortestDist);

                    // Identify & tree from all candidate nodes
                    int[] ref = new int[graph.getNodeCount()];
                    Arrays.fill(ref,-1);
                    int j = 0;
                    for(int i = 0 ; i < graph.getNodeCount() ; i++) {
                        double origCost = origTree.getCost(i);
                        double destCost = destTree.getCost(i);
                         if(origCost + destCost <= shortestDist * this.distDetour) {
                             ref[i] = j;
                             j++;
                         }
                    }
                    log.info(tripName + ": Identified " + j + " candidate nodes");
                    log.info(tripName + ": Creating shortest cost matrix...");
                    double[][] matrix = new double[j][j];
                    SimpleTree tree = new SimpleTree(graph);
                    for(int i = 0 ; i < graph.getNodeCount() ; i++) {
                        if(ref[i] != -1) {
                            tree.calculate(startNodeIdx,endNodeIdx,i,-1,true);
                            for(int k = 1 ; k < graph.getNodeCount() ; k++) {
                                if(ref[k] != -1) {
                                    matrix[ref[i]][ref[k]] = tree.getCost(k);
                                }
                            }
                        }
                    }
                    assert shortestDist == matrix[ref[startNodeIdx]][ref[endNodeIdx]];

                    // Explore
                    log.info(tripName + ": Exploring...");
                    TreeNode origin = new TreeNode(startNodeIdx,null,null,0.);
                    Set<TreeNode> paths = new LinkedHashSet<>();
                    explore(origin,paths,matrix,ref,endNodeIdx,shortestDist);

                    // Print counters
                    log.info(tripName + " RESULTS:" +
                            "\n Links evaluated: " + c1 +
                            "\n Nodes within overall limit: " + c2 +
                            "\n Non-looping nodes: " + c3 +
                            "\n Nodes within internal limit: " + c4 +
                            "\n ROUTES WITHIN " + this.distDetour + " LIMIT: " + paths.size());

                    int i = 0;
                    for(TreeNode path : paths) {
                        i++;

                        TreeNode curr = path;
                        double time = 0.;
                        double vgvi = 0.;
                        double shannon = 0.;
                        double POIs = 0.;
                        double negPOIs = 0.;
                        double crime = 0.;
                        double linkStress = 0.;
                        double jctStress = 0.;
                        while (curr.link != null) {
                            Link l = curr.link;
                            double dist = l.getLength();
                            time += travelTime.getLinkTravelTime(l,0.,PERSON,vehicle);
                            vgvi += dist * LinkAmbience.getVgviFactor(l);
                            shannon += dist * LinkAmbience.getShannonFactor(l);
                            POIs += (double) l.getAttributes().getAttribute("POIs");
                            negPOIs += (double) l.getAttributes().getAttribute("negPOIs");
                            crime += (double) l.getAttributes().getAttribute("crime");
                            linkStress += dist * LinkStress.getStress(l,trip.getMainMode());
                            jctStress += JctStress.getStress(l,trip.getMainMode());
                            curr = curr.parent;
                        }

                        Map<String,Object> results = new LinkedHashMap<>();
                        results.put("dist",path.cost);
                        results.put("time",time);
                        results.put("vgvi",vgvi);
                        results.put("shannon",shannon);
                        results.put("POIs",POIs);
                        results.put("negPOIs",negPOIs);
                        results.put("crime",crime);
                        results.put("linkStress",linkStress);
                        results.put("jctStress",jctStress);

                        // Add details to
                        String name = String.valueOf(i);
                        trip.setAttributes(name,results);
                    }
                    trip.setPathTree(paths);
                }
            }
        }

        private void explore(TreeNode n, Set<TreeNode> results, double[][] matrix, int[] ref,
                             int destNodeIdx, double totalCost) {
            SpeedyGraph.LinkIterator outLI = graph.getOutLinkIterator();
            outLI.reset(n.nodeIdx);
            int refDestNodeIdx = ref[destNodeIdx];
            while (outLI.next()) {
                int toNodeIdx =  outLI.getToNodeIndex();
                int refToNodeIdx = ref[toNodeIdx];
                if(refToNodeIdx != -1) {
                    c1++;
                    double destCost = matrix[refToNodeIdx][refDestNodeIdx];
                    int linkIdx = outLI.getLinkIndex();
                    Link link = this.graph.getLink(linkIdx);
                    double linkCost = link.getLength();
                    if(n.cost + linkCost + destCost <= totalCost * this.distDetour) {
                        c2++;
                        // Check for loop
                        if(checkLoop(n,toNodeIdx)) {
                            c3++;
                            // Check internal detours
                            if(checkInternalDetour(n,linkCost,matrix,ref,refToNodeIdx)) {
                                c4++;
                                TreeNode newPath = new TreeNode(toNodeIdx,n,link,n.cost + linkCost);
                                if(toNodeIdx == destNodeIdx) {
                                    results.add(newPath);
                                } else {
                                    explore(newPath,results,matrix,ref,destNodeIdx,totalCost);
                                }
                            }
                        }
                    }
                }
            }
        }

        public boolean checkInternalDetour(TreeNode n, double linkCost, double[][] matrix, int[] ref, int refToNodeIdx) {

            double pathCost = n.cost + linkCost;

            TreeNode curr = n;
            while (curr != null) {
                double shortestDistance = matrix[ref[curr.nodeIdx]][refToNodeIdx];
                double pathDistance = pathCost - curr.cost;
                if(pathDistance > shortestDistance * this.distDetour) {
                    return false;
                }
                curr = curr.parent;
            }
            return true;
        }

        public boolean checkLoop(TreeNode n, int nodeIdx) {
            TreeNode curr = n;
            while (curr != null) {
                if (curr.nodeIdx == nodeIdx) {
                    return false;
                }
                curr = curr.parent;
            }
            return true;
        }
    }
}
