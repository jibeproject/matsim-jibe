package trads;

import resources.Properties;
import resources.Resources;
import routing.ActiveAttributes;
import routing.Bicycle;
import routing.TravelAttribute;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
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
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trip.Trip;

import java.io.*;
import java.util.*;

import static trip.Place.*;

public class RunTradsAnalysis {

    private final static Logger logger = Logger.getLogger(RunTradsAnalysis.class);

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

        // Travel time
        FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        TravelTime ttBike = bicycle.getTravelTime();
        TravelTime ttWalk = new WalkTravelTime();

        // CALCULATOR
        TradsCalculator calc = new TradsCalculator(trips);

        // Extra attributes
        LinkedHashMap<String,TravelAttribute> bikeAttributes = ActiveAttributes.getJibe(TransportMode.bike,bike);
        LinkedHashMap<String,TravelAttribute> walkAttributes = ActiveAttributes.getJibe(TransportMode.walk,null);

        // beeline
        calc.beeline("beeline_orig_dest", ORIGIN, DESTINATION);
        calc.beeline("beeline_home_dest", HOME, DESTINATION);

        // car (freespeed only)
        calc.network("car", ORIGIN, DESTINATION, null, networkCar, carXy2l, freeSpeed, freeSpeed, null,false);

        // bike (shortest, fastest, and jibe)
        calc.network("bike_short", ORIGIN, DESTINATION,  bike, networkBike, null, new DistanceDisutility(), ttBike, bikeAttributes,false);
        calc.network("bike_fast", ORIGIN, DESTINATION,  bike, networkBike, null, new OnlyTimeDependentTravelDisutility(ttBike), ttBike, bikeAttributes,false);
        calc.network("bike_jibe", ORIGIN, DESTINATION, bike, networkBike, null, new JibeDisutility(TransportMode.bike,ttBike), ttBike, bikeAttributes,false);

        // walk (shortest, fastest, and jibe)
        calc.network("walk_short", ORIGIN, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, walkAttributes,false);
        calc.network("walk_fast", ORIGIN, DESTINATION, null, networkWalk, null, new OnlyTimeDependentTravelDisutility(ttWalk), ttWalk, walkAttributes,false);
        calc.network("walk_jibe", ORIGIN, DESTINATION, null, networkWalk, null, new JibeDisutility(TransportMode.walk,ttWalk), ttWalk, walkAttributes,false);

        // distance from home (use walk shortest for this)
        calc.network("home", HOME, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, null,false);

        // public transport
        calc.pt("pt", ORIGIN, DESTINATION, config, transitScheduleFilePath, transitNetworkFilePath);

        // Write results
        logger.info("Writing results to csv file...");
        TradsCsvWriter.write(trips, outputFile, calc.getAllAttributeNames());
    }

}
