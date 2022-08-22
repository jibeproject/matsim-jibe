package trads;

import ch.sbb.matsim.analysis.TravelAttribute;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class TradsCalculator {

    private final static Logger logger = Logger.getLogger(TradsCalculator.class);
    private final int numberOfThreads;
    private final Map<String,LinkedHashMap<String,TravelAttribute>> attributes;

    public TradsCalculator(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        this.attributes = new LinkedHashMap<>();
    }

    Map<String,LinkedHashMap<String,TravelAttribute>> getAttributes() {
        return attributes;
    }

    void calculate(Set<TradsTrip> trips, String route, Vehicle vehicle,
                          Network network, Network xy2lNetwork,
                          TravelDisutility travelDisutility, TravelTime travelTime,
                          LinkedHashMap<String,TravelAttribute> travelAttributes) {

        logger.info("Calculating indicators for route " + route);

        // add attribute data
        attributes.put(route, travelAttributes);

        // do calculation
        ConcurrentLinkedQueue<TradsTrip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter(route + ": Route ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            LeastCostPathCalculator dijkstra = new FastDijkstraFactory(false).
                    createPathCalculator(network, travelDisutility, travelTime);
            NetworkIndicatorCalculator worker = new NetworkIndicatorCalculator(odPairsQueue, counter, route, vehicle,
                    network, xy2lNetwork, dijkstra, travelDisutility, travelAttributes);
            threads[i] = new Thread(worker, "IndicatorCalculator-" + route + "-" + i);
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

    private static class NetworkIndicatorCalculator implements Runnable {

        private final ConcurrentLinkedQueue<TradsTrip> trips;
        private final Counter counter;
        private final String route;
        private final Vehicle vehicle;

        private final LeastCostPathCalculator pathCalculator;

        private final TravelDisutility travelDisutility;
        private final Network routingNetwork;
        private final Network xy2lNetwork;
        private final LinkedHashMap<String, TravelAttribute> travelAttributes;

        NetworkIndicatorCalculator(ConcurrentLinkedQueue<TradsTrip> trips, Counter counter, String route, Vehicle vehicle,
                                   Network routingNetwork, Network xy2lNetwork,
                                   LeastCostPathCalculator pathCalculator, TravelDisutility travelDisutility,
                                   LinkedHashMap<String, TravelAttribute> attributes) {
            this.trips = trips;
            this.counter = counter;
            this.route = route;
            this.vehicle = vehicle;
            this.routingNetwork = routingNetwork;
            this.xy2lNetwork = xy2lNetwork;
            this.pathCalculator = pathCalculator;
            this.travelDisutility = travelDisutility;
            this.travelAttributes = attributes;
        }

        public void run() {

            while(true) {
                TradsTrip trip = this.trips.poll();
                if(trip == null) {
                    return;
                }

                this.counter.incCounter();

                if(trip.isTripWithinBoundary()) {
                    Coord cOrig = trip.getOrigCoord();
                    Coord cDest = trip.getDestCoord();
                    Node nOrig;
                    Node nDest;
                    if(xy2lNetwork == null) {
                        nOrig = NetworkUtils.getNearestNode(routingNetwork,cOrig);
                        nDest = NetworkUtils.getNearestNode(routingNetwork,cDest);
                    } else {
                        nOrig = routingNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2lNetwork, cOrig).getToNode().getId());
                        nDest = routingNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2lNetwork, cDest).getToNode().getId());
                    }

                    // Calculate least cost path
                    LeastCostPathCalculator.Path path = pathCalculator.calcLeastCostPath(nOrig, nDest, 28800, null, vehicle);

                    // Set cost, time, and distance
                    trip.setCost(route, path.travelCost);
                    trip.setTime(route, path.travelTime);
                    trip.setDist(route, path.links.stream().mapToDouble(Link::getLength).sum());

                    // Set attribute results
                    if(travelAttributes != null) {
                        Map<String,Object> attributeResults = new LinkedHashMap<>();
                        for (Map.Entry<String,TravelAttribute> e : travelAttributes.entrySet()) {
                            String name = e.getKey();
                            double result = path.links.stream().mapToDouble(l -> e.getValue().getTravelAttribute(l,travelDisutility)).sum();
                            attributeResults.put(name,result);
                        }
                        trip.setAttributes(route,attributeResults);
                    }
                } else {
                    // Set everything to NaN
                    trip.setCost(route,Double.NaN);
                    trip.setTime(route,Double.NaN);
                    trip.setDist(route,Double.NaN);
                    if(travelAttributes != null) {
                        trip.setAttributes(route,travelAttributes.entrySet().stream().collect(
                                Collectors.toMap(Map.Entry::getKey, e -> Double.NaN)));
                    }
                }
            }
        }
    }
}
