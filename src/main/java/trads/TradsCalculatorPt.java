package trads;

import ch.sbb.matsim.routing.pt.raptor.*;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.Config;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.Facility;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class TradsCalculatorPt {

    private final static Logger logger = Logger.getLogger(TradsCalculatorPt.class);

    private final int numberOfThreads;

    private static final List<String> attributeNames = Arrays.asList("ptLegs","walkLegs","ptTravelTime","walkTravelTime",
            "totalTravelTime","ptModes","ptLegTravelTimes","walkLegTravelTimes","walkLegTravelDistances",
            "accessDistance","egressDistance","walkDistance");

    TradsCalculatorPt(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    List<String> getPtAttributes() {
        return attributeNames;
    }

    void calculate(Set<TradsTrip> trips, Config config, String scheduleFilePath, String networkFilePath) {

        config.transit().setUseTransit(true);
        Scenario scenario = ScenarioUtils.createScenario(config);

        logger.info("loading schedule from " + scheduleFilePath);
        new TransitScheduleReader(scenario).readFile(scheduleFilePath);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilePath);

        logger.info("preparing PT route calculation");
        RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(config);
        raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToOneRouting);
        SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), scenario.getTransitVehicles(), raptorConfig, scenario.getNetwork(), null);

        ConcurrentLinkedQueue<TradsTrip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter("Routing PT trip ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            SwissRailRaptor.Builder builder = new SwissRailRaptor.Builder(raptorData, config);
            Map<String, RoutingModule> accessRoutingModules = new HashMap<>();
            accessRoutingModules.put("walk",new TeleportationRoutingModule("walk",scenario,5.3 / 3.6,1.));
            builder.with(new DefaultRaptorStopFinder(new DefaultRaptorIntermodalAccessEgress(),accessRoutingModules));
            SwissRailRaptor raptor = builder.build();
//            SwissRailRaptor raptor = new SwissRailRaptor(raptorData,
//                    new DefaultRaptorParametersForPerson(config),
//                    new ConfigurableRaptorRouteSelector(),
//                    new DefaultRaptorStopFinder(new DefaultRaptorIntermodalAccessEgress(), accessRoutingModules),
//                    new DefaultRaptorInVehicleCostCalculator(),
//                    new DefaultRaptorTransferCostCalculator());
            ActivityFacilitiesFactoryImpl activityFacilitiesFactory = new ActivityFacilitiesFactoryImpl();

            PtIndicatorCalculator worker = new PtIndicatorCalculator(odPairsQueue, counter, scenario,raptor, activityFacilitiesFactory);
            threads[i] = new Thread(worker, "PT router thread " + i);
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

    static class PtIndicatorCalculator implements Runnable {

        private final ConcurrentLinkedQueue<TradsTrip> trips;
        private final Counter counter;
        private final SwissRailRaptor raptor;
        private final Scenario scenario;
        private final ActivityFacilitiesFactory activityFacilitiesFactory;

        PtIndicatorCalculator(ConcurrentLinkedQueue<TradsTrip> trips, Counter counter, Scenario scenario, SwissRailRaptor raptor,
                              ActivityFacilitiesFactoryImpl activityFacilitiesFactory) {
            this.trips = trips;
            this.counter = counter;
            this.scenario = scenario;
            this.raptor = raptor;
            this.activityFacilitiesFactory = activityFacilitiesFactory;

        }

        public void run() {
            while(true) {
                TradsTrip trip = this.trips.poll();
                if(trip == null) {
                    return;
                }
                this.counter.incCounter();

                if(trip.isTripWithinBoundary()) {

                    int departureTime = trip.getStartTime();
                    int earliestDepartureTime = Math.max(departureTime - 900, 0);
                    int latestDepartureTime = Math.min(departureTime + 900, 86399);

                    Coord cOrig = trip.getOrigCoord();
                    Coord cDest = trip.getDestCoord();

                    Facility fOrig = activityFacilitiesFactory.createActivityFacility(Id.create(1, ActivityFacility.class), cOrig);
                    Facility fDest = activityFacilitiesFactory.createActivityFacility(Id.create(1, ActivityFacility.class), cDest);

                    List<Leg> legs = raptor.calcRoute(fOrig, fDest, earliestDepartureTime, departureTime, latestDepartureTime, null);

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
                    trip.setAttributes("pt", attributes(attributeValues));
                } else {
                    List<Object> attributeValues = Arrays.asList(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "NA", "NA",
                            "NA", "NA", Double.NaN, Double.NaN, Double.NaN);
                    trip.setAttributes("pt",attributes(attributeValues));
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
}
