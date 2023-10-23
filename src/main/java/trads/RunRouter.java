package trads;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelTime;
import routing.Bicycle;
import routing.disutility.DistanceDisutility;
import routing.travelTime.WalkTravelTime;
import trads.calculate.RouteIndicatorCalculator;
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trads.io.TradsRouteWriter;
import trip.Trip;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static trip.Place.*;

public class RunRouter {

    private final static Logger logger = Logger.getLogger(RunRouter.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output file");
        }

        Resources.initializeResources(args[0]);
        String outputFile = args[1];

        boolean savePath;
        if (outputFile.endsWith(".csv")) {
            savePath = false;
        } else if (outputFile.endsWith(".gpkg")) {
            savePath = true;
        } else {
            throw new RuntimeException("Unrecognised file output suffix. Please use csv or gpkg.");
        }

//        String transitScheduleFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_SCHEDULE);
//        String transitNetworkFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_NETWORK);


        // Set up scenario and config
        Config config = ConfigUtils.createConfig();
        Bicycle bicycle = new Bicycle(config);
        Vehicle bike = bicycle.getVehicle();

        // Create car networks
        logger.info("Creating mode-specific networks...");
        Network networkCar = NetworkUtils.createNetwork();
        new MatsimNetworkReader(networkCar).readFile(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));
        Network carXy2l = NetworkUtils2.extractXy2LinksNetwork(networkCar, l -> !((boolean) l.getAttributes().getAttribute("motorway")));

        // Create active mode networks
        Network network = NetworkUtils2.readFullNetwork();
        Network networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);//
//                .stream()
//                .filter(t -> (t.getMainMode().equals("Car or van passenger") || t.getMainMode().equals("Car or van driver")))
//                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Travel time
        FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        TravelTime ttBike = bicycle.getTravelTime();
        TravelTime ttWalk = new WalkTravelTime();

        // Car freespeed & congested travel time
        String tfgmDemandEvents = Resources.instance.getString(Properties.MATSIM_TFGM_OUTPUT_EVENTS);
        TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(networkCar);
        TravelTimeCalculator congested = builder.build();
        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(congested);
        (new MatsimEventsReader(events)).readFile(tfgmDemandEvents);
        TravelTime congestedTime = congested.getLinkTravelTimes();
        TravelDisutility congestedDisutility = new OnlyTimeDependentTravelDisutility(congested.getLinkTravelTimes());

        // CALCULATOR
        RouteIndicatorCalculator calc = new RouteIndicatorCalculator(trips);

        // beeline
        calc.beeline("beeline", ORIGIN, DESTINATION);

        // car (freespeed only)
        calc.network("car_freespeed", ORIGIN, DESTINATION, null, networkCar, carXy2l, freeSpeed, freeSpeed, null,savePath);
        calc.network("car_congested", ORIGIN, DESTINATION, null, networkCar, carXy2l, congestedDisutility, congestedTime, null,savePath);

        // bike (shortest and fastest)
        calc.network("bike_short", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new DistanceDisutility(), ttBike, null,savePath);
        calc.network("bike_fast", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new OnlyTimeDependentTravelDisutility(ttBike), ttBike, null,savePath);

        calc.network("walk_short", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new DistanceDisutility(), ttWalk, null,savePath);
        calc.network("walk_fast", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new OnlyTimeDependentTravelDisutility(ttWalk), ttWalk, null,savePath);
//
//        // public transport
//        calc.pt("pt", ORIGIN, DESTINATION, config, transitScheduleFilePath, transitNetworkFilePath);

//        // Activity-based modelling calculations (not relevant for JIBE)
//        calc.beeline("beeline_hs", HOME, DESTINATION);
//        calc.beeline("beeline_sm", DESTINATION, MAIN);
//        calc.beeline("beeline_hm", HOME,MAIN);
//        calc.network("car_hs", HOME, DESTINATION, null, networkCar, carXy2l, freeSpeed, freeSpeed, null,false);
//        calc.network("car_sm",DESTINATION,MAIN,null, networkCar, carXy2l, freeSpeed, freeSpeed, null,false);
//        calc.network("car_hm",HOME,MAIN,null, networkCar, carXy2l, freeSpeed, freeSpeed, null,false);

        // Write results
        if (outputFile.endsWith(".csv")) {
            logger.info("Writing results to csv file...");
            TradsCsvWriter.write(trips, outputFile, calc.getAllAttributeNames());
        } else {
            logger.info("Writing results to gpkg file...");
            TradsRouteWriter.write(trips,outputFile,calc.getAllAttributeNames());
        }
    }
}
