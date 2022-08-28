package trads;

import ch.sbb.matsim.analysis.TravelAttribute;
import data.Place;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import trads.calculate.BeelineCalculator;
import trads.calculate.NetworkIndicatorCalculator;
import ch.sbb.matsim.routing.pt.raptor.*;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import trads.calculate.PtIndicatorCalculator;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TradsCalculator {

    private final static Logger logger = Logger.getLogger(TradsCalculator.class);
    private final int numberOfThreads;
    private final Set<TradsTrip> trips;
    private final Map<String, List<String>> allAttributeNames;

    public TradsCalculator(int numberOfThreads, Set<TradsTrip> trips) {
        this.numberOfThreads = numberOfThreads;
        this.trips = trips;
        this.allAttributeNames = new LinkedHashMap<>();
    }

    Map<String,List<String>> getAllAttributeNames() { return allAttributeNames; }

    void network(String route, Place origin, Place destination, Vehicle vehicle,
                 Network network, Network xy2lNetwork,
                 TravelDisutility travelDisutility, TravelTime travelTime,
                 LinkedHashMap<String,TravelAttribute> additionalAttributes) {

        logger.info("Calculating network indicators for route " + route);

        // Specify attribute names
        List<String> attributeNames = new ArrayList<>(List.of("cost","time","dist"));
        if(additionalAttributes != null) {
            attributeNames.addAll(additionalAttributes.keySet());
        }
        allAttributeNames.put(route, attributeNames);

        // Do calculation
        ConcurrentLinkedQueue<TradsTrip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter(route + ": Route ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            LeastCostPathCalculator dijkstra = new FastDijkstraFactory(false).
                    createPathCalculator(network, travelDisutility, travelTime);
            NetworkIndicatorCalculator worker = new NetworkIndicatorCalculator(odPairsQueue, counter, route,
                    origin, destination, vehicle, network, xy2lNetwork, dijkstra, travelDisutility, additionalAttributes);
            threads[i] = new Thread(worker, "NetworkCalculator-" + route + "-" + i);
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

    void pt(String route, Place origin, Place destination, Config config, String scheduleFilePath, String networkFilePath) {

        config.transit().setUseTransit(true);
        Scenario scenario = ScenarioUtils.createScenario(config);

        // Specify attribute names
        List<String> attributeNames = List.of("ptLegs","walkLegs","ptTravelTime","walkTravelTime",
                "totalTravelTime","ptModes","ptLegTravelTimes","walkLegTravelTimes","walkLegTravelDistances",
                "accessDistance","egressDistance","walkDistance");
        allAttributeNames.put(route, attributeNames);

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
            ActivityFacilitiesFactoryImpl activityFacilitiesFactory = new ActivityFacilitiesFactoryImpl();

            PtIndicatorCalculator worker = new PtIndicatorCalculator(odPairsQueue, route, counter, origin,
                    destination, scenario,raptor, activityFacilitiesFactory, attributeNames);
            threads[i] = new Thread(worker, "PublicTransportCalculator-" + i);
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

    void beeline(String route, Place origin, Place destination) {

        logger.info("Calculating beeline distances for route " + route);

        // Specify attribute names
        allAttributeNames.put(route, List.of(""));

        // do calculation
        ConcurrentLinkedQueue<TradsTrip> odPairsQueue = new ConcurrentLinkedQueue<>(trips);

        Counter counter = new Counter(route + ": Route ", " / " + trips.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            BeelineCalculator worker = new BeelineCalculator(odPairsQueue, counter, route, origin, destination);
            threads[i] = new Thread(worker, "BeelineCalculator-" + route + "-" + i);
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
}
