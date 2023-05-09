/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package trads.calculate;

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
import routing.graph.MultiTree;
import routing.graph.SpeedyGraph;
import trip.Place;
import trip.Trip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;


public final class MultiRouteCalculator {

    private MultiRouteCalculator() {
    }

    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public static void calculate(Set<Trip> trips, Place origin, Place destination,
                                                 Network routingNetwork, Network xy2lNetwork,
                                                 TravelTime travelTime, Vehicle vehicle) {

        int numberOfThreads = 1;//Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        SpeedyGraph graph = new SpeedyGraph(routingNetwork,null,null,null,null);

        // prepare calculation
        ConcurrentLinkedQueue<Trip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter("Calculating multi-route node ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RouteWorker worker = new RouteWorker(odPairsQueue, counter, origin, destination,
                    vehicle, routingNetwork, xy2lNetwork, graph, travelTime);
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

        public RouteWorker(ConcurrentLinkedQueue<Trip> trips, Counter counter,
                           Place origin, Place destination, Vehicle vehicle,
                           Network routingNetwork, Network xy2lNetwork, SpeedyGraph graph,
                           TravelTime travelTime) {
            this.trips = trips;
            this.counter = counter;
            this.origin = origin;
            this.destination = destination;
            this.vehicle = vehicle;
            this.routingNetwork = routingNetwork;
            this.xy2lNetwork = xy2lNetwork;
            this.graph = graph;
            this.travelTime = travelTime;
        }

        public void run() {

            MultiTree mTree = new MultiTree(graph);

            while(true) {
                Trip trip = this.trips.poll();
                if(trip == null) {
                    return;
                }

                this.counter.incCounter();

                if(trip.routable(origin, destination)) {
                    Coord cOrig = trip.getCoord(origin);
                    Coord cDest = trip.getCoord(destination);
                    Node nOrig = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cOrig).getToNode().getId());
                    Node nDest = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cDest).getToNode().getId());

                    // Calculate multi tree
                    mTree.calculate(nOrig.getId().index(),nDest.getId().index(),1.1);
                    Set<List<Link>> allPaths = mTree.getAllPaths(nDest.getId().index());

                    // Set cost and time
                    int i = 0;
                    for(List<Link> path : allPaths) {
                        i++;
                        String name = String.valueOf(i);
                        Map<String,Object> results = new LinkedHashMap<>();
                        double dist = path.stream().mapToDouble(Link::getLength).sum();
                        double time = path.stream().mapToDouble(l -> travelTime.getLinkTravelTime(l,0.,PERSON,vehicle)).sum();
                        int[] edgeIDs = path.stream().mapToInt(l -> (int) l.getAttributes().getAttribute("edgeID")).toArray();

                        results.put("dist",dist);
                        results.put("time",time);
                        trip.setAttributes(name,results);
                        trip.setRoutePath(name,nOrig.getCoord(),edgeIDs,dist,time);
                    }
                }
            }
        }
    }
}
