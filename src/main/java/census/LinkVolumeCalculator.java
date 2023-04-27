package census;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import resources.Properties;
import resources.Resources;
import trip.Place;
import trip.Trip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LinkVolumeCalculator {

    private final static Logger logger = Logger.getLogger(LinkVolumeCalculator.class);
    private final Set<Trip> trips;

    private final Map<String,int[]> allResults;

    public LinkVolumeCalculator(Set<Trip> trips) {
        this.trips = trips;
        this.allResults = new LinkedHashMap<>();
    }

    public Map<String, int[]> getAllResults() {
        return allResults;
    }

    public int[] calculate(String route, Place origin, Place destination, Vehicle vehicle,
                           Network network, Network xy2lNetwork,
                           TravelDisutility travelDisutility, TravelTime travelTime) {

        logger.info("Calculating network volumes for route " + route);

        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);

        // Do calculation
        ConcurrentLinkedQueue<Trip> tripsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter(route + ": Route ", " / " + trips.size());
        TripWorker[] workers = new TripWorker[numberOfThreads];
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            LeastCostPathCalculator dijkstra = new FastDijkstraFactory(false).
                    createPathCalculator(network, travelDisutility, travelTime);
            workers[i] = new TripWorker(tripsQueue, counter, origin, destination, vehicle, network, xy2lNetwork, dijkstra);
            threads[i] = new Thread(workers[i], "LinkVolumeCalculator-" + route + "-" + i);
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

        // Add up results from individual threads
        int size = Id.getNumberOfIds(Link.class);
        int[] results = new int[size];
        for (int i = 0; i < numberOfThreads; i++) {
            int[] threadResults = workers[i].getLinkVolumes();
            for (int j = 0; j < size; j++) {
                results[j] += threadResults[j];
            }
        }

        allResults.put(route,results);
        return results;
    }


    private static class TripWorker implements Runnable {

        private final ConcurrentLinkedQueue<Trip> trips;
        private final Counter counter;
        private final Vehicle vehicle;
        private final Place origin;
        private final Place destination;
        private final LeastCostPathCalculator pathCalculator;
        private final Network routingNetwork;
        private final Network xy2lNetwork;
        private final int[] results;

        public TripWorker(ConcurrentLinkedQueue<Trip> trips, Counter counter,
                          Place origin, Place destination, Vehicle vehicle,
                          Network routingNetwork, Network xy2lNetwork,
                          LeastCostPathCalculator pathCalculator) {
            this.trips = trips;
            this.counter = counter;
            this.origin = origin;
            this.destination = destination;
            this.vehicle = vehicle;
            this.routingNetwork = routingNetwork;
            this.xy2lNetwork = xy2lNetwork;
            this.pathCalculator = pathCalculator;
            this.results = new int[Id.getNumberOfIds(Link.class)];
        }

        public void run() {

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

                    for(Link link : pathCalculator.calcLeastCostPath(nOrig, nDest, 0., null, vehicle).links) {
                        results[link.getId().index()]++;
                    }
                }
            }
        }

        public int[] getLinkVolumes() {
            return results;
        }
    }
}
