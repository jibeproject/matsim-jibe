package trads.calculate;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import resources.Properties;
import resources.Resources;
import trip.Trip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class LogitDataCalculator {

    private final static Logger logger = Logger.getLogger(LogitDataCalculator.class);
    private final int numberOfThreads;
    private final Set<Trip> trips;

    public LogitDataCalculator(Set<Trip> trips) {
        this.numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        this.trips = trips;
    }

    public void calculate(Vehicle vehicle, Network network,
                          TravelDisutility travelDisutility, TravelTime travelTime) {

        logger.info("Calculating logitData indicators.");

        // Do calculation
        ConcurrentLinkedQueue<Trip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter("Route ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            LeastCostPathCalculator dijkstra = new FastDijkstraFactory(false).
                    createPathCalculator(network, travelDisutility, travelTime);
            TripWorker worker = new TripWorker(odPairsQueue, counter, vehicle, dijkstra);
            threads[i] = new Thread(worker, "LogitDataCalculator-" + i);
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

    static class TripWorker implements Runnable {

        private final ConcurrentLinkedQueue<Trip> trips;
        private final Counter counter;

        private final Vehicle vehicle;

        private final LeastCostPathCalculator pathCalculator;


        public TripWorker(ConcurrentLinkedQueue<Trip> trips, Counter counter,
                          Vehicle vehicle, LeastCostPathCalculator pathCalculator) {
            this.trips = trips;
            this.counter = counter;
            this.vehicle = vehicle;
            this.pathCalculator = pathCalculator;
        }

        public void run() {

            while(true) {
                Trip trip = this.trips.poll();
                if(trip == null) {
                    return;
                }

                this.counter.incCounter();
                LeastCostPathCalculator.Path path = pathCalculator.calcLeastCostPath(trip.getOrigNode(), trip.getDestNode(), trip.getStartTime(), null, vehicle);
                trip.addPath(path.links.stream().map(Identifiable::getId).collect(Collectors.toList()));
            }
        }
    }

}
