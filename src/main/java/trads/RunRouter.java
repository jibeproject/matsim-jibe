package trads;

import resources.Properties;
import resources.Resources;
import routing.Bicycle;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import routing.disutility.DistanceDisutility;
import routing.travelTime.WalkTravelTime;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import trads.calculate.RouteIndicatorCalculator;
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trip.Trip;

import java.io.*;
import java.util.*;

import static trip.Place.*;

public class RunRouter {

    private final static Logger logger = Logger.getLogger(RunRouter.class);

    public static void main(String[] args) throws IOException {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output file");
        }

        Resources.initializeResources(args[0]);
        String outputFile = args[1];

        String transitScheduleFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_SCHEDULE);
        String transitNetworkFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_NETWORK);

        // Read network
        Network network = NetworkUtils2.readFullNetwork();

        // Set up scenario and config
        Config config = ConfigUtils.createConfig();
        Bicycle bicycle = new Bicycle(config);
        Vehicle bike = bicycle.getVehicle();

        // Create mode-specific networks
        logger.info("Creating mode-specific networks...");
        Network networkCar = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.car);
        Network carXy2l = NetworkUtils2.extractXy2LinksNetwork(networkCar, l -> !((boolean) l.getAttributes().getAttribute("motorway")));
        Network networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);
//                .stream()
//                .filter(t -> (t.getEndPurpose().isMandatory() && t.getStartPurpose().equals(TradsPurpose.HOME)) ||
//                        (t.getStartPurpose().isMandatory() && t.getEndPurpose().equals(TradsPurpose.HOME)))
//                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Travel time
        FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        TravelTime ttBike = bicycle.getTravelTime();
        TravelTime ttWalk = new WalkTravelTime();

        // CALCULATOR
        RouteIndicatorCalculator calc = new RouteIndicatorCalculator(trips);

        // beeline
        calc.beeline("beeline_orig_dest", ORIGIN, DESTINATION);
        calc.beeline("beeline_home_dest", HOME, DESTINATION);

        // car (freespeed only)
        calc.network("car", ORIGIN, DESTINATION, null, networkCar, carXy2l, freeSpeed, freeSpeed, null,false);

        // bike (shortest, fastest, and jibe)
        calc.network("bike_short", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new DistanceDisutility(), ttBike, null,false);
        calc.network("bike_fast", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new OnlyTimeDependentTravelDisutility(ttBike), ttBike, null,false);

        calc.network("walk_short", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new DistanceDisutility(), ttWalk, null,false);
        calc.network("walk_fast", ORIGIN, DESTINATION, null, networkWalk, networkWalk, new OnlyTimeDependentTravelDisutility(ttWalk), ttWalk, null,false);

        // public transport
        calc.pt("pt", ORIGIN, DESTINATION, config, transitScheduleFilePath, transitNetworkFilePath);

        // Write results
        logger.info("Writing results to csv file...");
        TradsCsvWriter.write(trips, outputFile, calc.getAllAttributeNames());
    }

}
