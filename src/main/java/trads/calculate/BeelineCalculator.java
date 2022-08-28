package trads.calculate;

import data.Place;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import trads.TradsTrip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BeelineCalculator implements Runnable {

    private final ConcurrentLinkedQueue<TradsTrip> trips;
    private final Counter counter;
    private final String route;

    private final Place origin;
    private final Place destination;

    public BeelineCalculator(ConcurrentLinkedQueue<TradsTrip> trips, Counter counter, String route,
                      Place origin, Place destination) {
        this.trips = trips;
        this.counter = counter;
        this.route = route;
        this.origin = origin;
        this.destination = destination;
    }

    public void run() {
        while(true) {
            TradsTrip trip = this.trips.poll();
            if(trip == null) {
                return;
            }
            this.counter.incCounter();
            Coord cOrig = trip.getCoord(origin);
            Coord cDest = trip.getCoord(destination);
            if(cOrig != null && cDest != null) {
                double dist = CoordUtils.calcEuclideanDistance(cOrig,cDest);
                trip.setAttributes(route,Map.of("", dist));
            }
        }
    }
}
