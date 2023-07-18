package trads.calculate;
import ch.sbb.matsim.routing.pt.raptor.*;
import trip.Place;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.Facility;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import trip.Trip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class PtIndicatorCalculator implements Runnable {

    private final ConcurrentLinkedQueue<Trip> trips;
    private final String route;
    private final Counter counter;
    private final Place origin;
    private final Place destination;
    private final SwissRailRaptor raptor;
    private final Scenario scenario;
    private final ActivityFacilitiesFactory activityFacilitiesFactory;
    private final List<String> attributeNames;

    public PtIndicatorCalculator(ConcurrentLinkedQueue<Trip> trips, String route, Counter counter, Place origin, Place destination,
                                 Scenario scenario, SwissRailRaptor raptor,
                                 ActivityFacilitiesFactoryImpl activityFacilitiesFactory, List<String> attributeNames) {
        this.trips = trips;
        this.route = route;
        this.counter = counter;
        this.origin = origin;
        this.destination = destination;
        this.scenario = scenario;
        this.raptor = raptor;
        this.activityFacilitiesFactory = activityFacilitiesFactory;
        this.attributeNames = attributeNames;
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

                int departureTime = trip.getStartTime();
                int earliestDepartureTime = Math.max(departureTime - 900, 0);
                int latestDepartureTime = Math.min(departureTime + 900, 86399);

                Facility fOrig = activityFacilitiesFactory.createActivityFacility(Id.create(1, ActivityFacility.class), cOrig);
                Facility fDest = activityFacilitiesFactory.createActivityFacility(Id.create(1, ActivityFacility.class), cDest);

                List<Leg> legs = null;//raptor.calcRoute(fOrig, fDest, earliestDepartureTime, departureTime, latestDepartureTime, null); todo: adapt to MATSim 14.0

                int ptLegs = 0;
                int walkLegs = 0;
                double ptTime = 0.;
                double walkTime = 0.;
                double walkDistance = 0.;
                ArrayList<String> ptLegModes = new ArrayList<>();
                ArrayList<Double> ptLegTravelTimes = new ArrayList<>();
                ArrayList<Double> walkLegTravelTimes = new ArrayList<>();
                ArrayList<Double> walkLegTravelDistances = new ArrayList<>();

                if (legs != null) {
                    for (Leg leg : legs) {
                        String mode = leg.getMode();
                        double travelTime = leg.getTravelTime().seconds();
                        if (mode.equals("pt")) {
                            ptLegs++;
                            DefaultTransitPassengerRoute route = (DefaultTransitPassengerRoute) leg.getRoute();
                            ptLegModes.add(scenario.getTransitSchedule().getTransitLines().get(route.getLineId()).getRoutes().get(route.getRouteId()).getTransportMode());
                            ptLegTravelTimes.add(travelTime);
                            ptTime += travelTime;
                        } else if (mode.equals("walk")) {
                            walkLegs++;
                            walkLegTravelTimes.add(travelTime);
                            walkLegTravelDistances.add(leg.getRoute().getDistance());
                            walkTime += travelTime;
                            walkDistance += leg.getRoute().getDistance();
                        } else {
                            throw new RuntimeException("Unknown transit leg mode " + mode);
                        }
                    }
                }
                double totalTravelTime = ptTime + walkTime;

                Double accessDistance = null;
                Double egressDistance = null;

                if (legs != null) {
                    Leg firstLeg = legs.get(0);
                    Leg lastLeg = legs.get(legs.size() - 1);
                    if (!lastLeg.getMode().equals("walk") || !firstLeg.getMode().equals("walk")) {
                        throw new RuntimeException("First or last leg of trip " + trip.getTripId() + " not walk!");
                    }

                    if (legs.size() > 1) {
                        accessDistance = firstLeg.getRoute().getDistance();
                        egressDistance = lastLeg.getRoute().getDistance();
                    }
                }

                String ptLegModesString = String.join("_", ptLegModes);
                String ptLegTravelTimesString = ptLegTravelTimes.stream().map(Object::toString).collect(Collectors.joining("_"));
                String walkLegTravelTimesString = walkLegTravelTimes.stream().map(Object::toString).collect(Collectors.joining("_"));
                String walkLegTravelDistancesString = walkLegTravelDistances.stream().map(v -> String.format(Locale.UK, "%.1f", v)).collect(Collectors.joining("_"));

                List<Object> attributeValues = Arrays.asList(ptLegs, walkLegs, ptTime, walkTime, totalTravelTime, ptLegModesString, ptLegTravelTimesString,
                        walkLegTravelTimesString, walkLegTravelDistancesString, accessDistance, egressDistance, walkDistance);
                trip.setAttributes(route, attributes(attributeValues));
            }
        }
    }

    private Map<String,Object> attributes(List<Object> values) {
        Map<String,Object> result = new LinkedHashMap<>();
        if(attributeNames.size() != values.size()) {
            throw new RuntimeException();
        }
        for(int i = 0 ; i < attributeNames.size() ; i++) {
            result.put(attributeNames.get(i),values.get(i));
        }
        return result;
    }

}