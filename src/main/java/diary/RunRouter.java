package diary;

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
import routing.disutility.JibeDisutility3Fast;
import routing.travelTime.WalkTravelTime;
import diary.calculate.RouteIndicatorCalculator;
import io.TripCsvWriter;
import io.DiaryReader;
import io.TripRouteWriter;
import trip.Trip;

import java.io.*;
import java.util.*;

import static trip.Place.*;

public class RunRouter {

    private final static Logger logger = Logger.getLogger(RunRouter.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Route data output (.csv) \n" +
                    "(2) Link data output (specify .gpkg OR write \"true\" to include links in csv file) \n");
        }

        Resources.initializeResources(args[0]);
        String outputCsv = args[1];
        String outputGpkg = null;
        boolean savePath = false;

        if(args[2] != null) {
            if (args[2].endsWith(".gpkg")) {
                outputGpkg = args[2];
                savePath = true;
            } else {
                savePath = Boolean.parseBoolean(args[2]);
            }
        }

        // Set up scenario and config
        Config config = ConfigUtils.createConfig();
        Bicycle bicycle = new Bicycle(config);
        Vehicle bike = bicycle.getVehicle();

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = DiaryReader.readTrips(boundary);//
//                .stream()
//                .filter(t -> (t.getMainMode().equals("Car or van passenger") || t.getMainMode().equals("Car or van driver")))
//                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Create car networks
        logger.info("Creating mode-specific networks...");
        Network networkCar = NetworkUtils.createNetwork();
        new MatsimNetworkReader(networkCar).readFile(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));
        Network carXy2l = NetworkUtils2.extractXy2LinksNetwork(networkCar, l -> !((boolean) l.getAttributes().getAttribute("motorway")));

        // Create active mode networks
        Network network = NetworkUtils2.readFullNetwork();
        Network networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

//        // Load public transport data
//        String transitScheduleFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_SCHEDULE);
//        String transitNetworkFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_NETWORK);

        // Travel time
        FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        TravelTime ttBikeFast = bicycle.getTravelTimeFast(networkBike,bike);
        TravelTime ttBike = bicycle.getTravelTime();
        TravelTime ttWalk = new WalkTravelTime();

        // Car freespeed & congested travel time
        String tfgmDemandEvents = Resources.instance.getString(Properties.MATSIM_DEMAND_OUTPUT_EVENTS);
        TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(networkCar);
        TravelTimeCalculator congested = builder.build();
        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(congested);
        (new MatsimEventsReader(events)).readFile(tfgmDemandEvents);
        TravelTime congestedTime = congested.getLinkTravelTimes();
        TravelDisutility congestedDisutility = new OnlyTimeDependentTravelDisutility(congested.getLinkTravelTimes());

        // CALCULATOR
        RouteIndicatorCalculator calc = new RouteIndicatorCalculator(trips);

//        // beeline
        calc.beeline("beeline", ORIGIN, DESTINATION);

        // car
        calc.network("car_freespeed", ORIGIN, DESTINATION, null, networkCar, carXy2l, freeSpeed, freeSpeed, null,savePath);
        calc.network("car_congested", ORIGIN, DESTINATION, null, networkCar, carXy2l, congestedDisutility, congestedTime, null,savePath);

         // bike
//        calc.network("bike_jibe_day", ORIGIN, DESTINATION, bike, networkBike, networkBike, new JibeDisutility3Fast(networkBike,bike,TransportMode.bike,ttBikeFast,true), ttBike, null,savePath);
//        calc.network("bike_jibe_night", ORIGIN, DESTINATION, bike, networkBike, networkBike, new JibeDisutility3Fast(networkBike,bike,TransportMode.bike,ttBikeFast,false), ttBike, null,savePath);
        calc.network("bike_short", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new DistanceDisutility(), ttBikeFast, null,savePath);
        calc.network("bike_fast", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new OnlyTimeDependentTravelDisutility(ttBikeFast), ttBike, null,savePath);

        // walk
//        calc.network("walk_jibe_day", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new JibeDisutility3Fast(networkWalk,null,TransportMode.walk,ttWalk,true), ttWalk, null, savePath);
//        calc.network("walk_jibe_night", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new JibeDisutility3Fast(networkWalk,null,TransportMode.walk,ttWalk,false), ttWalk, null, savePath);
        calc.network("walk_short", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new DistanceDisutility(), ttWalk, null,savePath);
        calc.network("walk_fast", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new OnlyTimeDependentTravelDisutility(ttWalk), ttWalk, null,savePath);

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
        if (outputCsv != null) {
            logger.info("Writing results to csv file...");
            TripCsvWriter.write(trips, outputCsv, calc.getAllAttributeNames());
        }

        if (outputGpkg != null) {
            logger.info("Writing results to gpkg file...");
            TripRouteWriter.write(trips,network,outputGpkg,false,calc.getAllAttributeNames());
        }
    }
}
