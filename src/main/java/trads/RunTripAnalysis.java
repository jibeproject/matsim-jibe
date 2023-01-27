package trads;

import ch.sbb.matsim.analysis.TravelAttribute;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAttractiveness;
import routing.disutility.components.LinkStress;
import routing.travelTime.WalkTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;
import routing.travelTime.BicycleTravelTime;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.*;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import trads.io.RouteAttributeWriter;
import trads.io.TradsReader;

import java.io.*;
import java.util.*;

import static data.Place.*;

// THIS SCRIPT IS FOR CONVERTING X/Y COORDINATES IN THE TRAVEL SURVEY TO PATH DATA (E.G. TRAVEL TIMES, DISTANCES, COSTS) USING THE MATSIM NETWORK
// NOTE: number of threads for PT is limited to the RAM available as the network needs to be duplicated (need about 9GB per thread)

public class RunTripAnalysis {

    private final static Logger logger = Logger.getLogger(RunTripAnalysis.class);
    private final static double MAX_BIKE_SPEED = 16 / 3.6;

    public static void main(String[] args) throws IOException {

        if(args.length != 7) {
            throw new RuntimeException("Program requires 7 arguments: \n" +
                    "(0) Survey File Path \n" +
                    "(1) Boundary Geopackage Path \n" +
                    "(2) Network File Path \n" +
                    "(3) Transit Schedule Path \n" +
                    "(4) Transit Network Path \n" +
                    "(5) Output File Path \n" +
                    "(6) Number of Threads \n");
        }

        String surveyFilePath = args[0];
        String boundaryFilePath = args[1];
        String networkFilePath = args[2];
        String transitScheduleFilePath = args[3];
        String transitNetworkFilePath = args[4];
        String outputFile = args[5];
        int numberOfThreads = Integer.parseInt(args[6]);

        // Read network
        logger.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFilePath);

        // Set up scenario and config
        logger.info("Preparing Matsim config and scenario...");
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode("bike");
        config.addModule(bicycleConfigGroup);

        // CREATE BICYCLE VEHICLE
        VehicleType type = VehicleUtils.createVehicleType(Id.create("bicycle", VehicleType.class));
        type.setMaximumVelocity(MAX_BIKE_SPEED);
        Vehicle bike = VehicleUtils.createVehicle(Id.createVehicleId(1), type);

        // Create mode-specific networks
        logger.info("Creating mode-specific networks...");
        Network networkCar = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.car);
        Network carXy2l = NetworkUtils2.extractXy2LinksNetwork(networkCar, l -> !((boolean) l.getAttributes().getAttribute("motorway")));
        Network networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readBoundary(boundaryFilePath);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<TradsTrip> trips = TradsReader.readTrips(surveyFilePath, boundary);

        // Limit to first N records (for debugging only)
//        trips = trips.stream().limit(200).collect(Collectors.toSet());

        // Travel time
        FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        TravelTime ttBike = new BicycleTravelTime(linkSpeedCalculator);
        TravelTime ttWalk = new WalkTravelTime();

        // Calculate network indicators
        logger.info("Calculating network indicators using " + numberOfThreads + " threads.");


        // CALCULATOR
        TradsCalculator calc = new TradsCalculator(10, trips);

        // beeline
        calc.beeline("beeline_orig_dest", ORIGIN, DESTINATION);
        calc.beeline("beeline_home_dest", HOME, DESTINATION);

        // car (freespeed only)
        calc.network("car", ORIGIN, DESTINATION, null, networkCar, carXy2l, freeSpeed, freeSpeed, null,false);

        // bike (shortest, fastest, and jibe)
        calc.network("bike_short", ORIGIN, DESTINATION,  bike, networkBike, null, new DistanceDisutility(), ttBike, activeAttributes(TransportMode.bike),false);
        calc.network("bike_fast", ORIGIN, DESTINATION,  bike, networkBike, null, new OnlyTimeDependentTravelDisutility(ttBike), ttBike, activeAttributes(TransportMode.bike),false);
        calc.network("bike_jibe", ORIGIN, DESTINATION, bike, networkBike, null, new JibeDisutility(TransportMode.bike,ttBike), ttBike, activeAttributes(TransportMode.bike),false);

        // walk (shortest, fastest, and jibe)
        calc.network("walk_short", ORIGIN, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, activeAttributes(TransportMode.walk),false);
        calc.network("walk_fast", ORIGIN, DESTINATION, null, networkWalk, null, new OnlyTimeDependentTravelDisutility(ttWalk), ttWalk, activeAttributes(TransportMode.walk),false);
        calc.network("walk_jibe", ORIGIN, DESTINATION, null, networkWalk, null, new JibeDisutility(TransportMode.walk,ttWalk), ttWalk, activeAttributes(TransportMode.walk),false);

        // distance from home (use walk shortest for this)
        calc.network("home", HOME, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, null,false);

        // public transport
        calc.pt("pt", ORIGIN, DESTINATION, config, transitScheduleFilePath, transitNetworkFilePath);

        // Write results
        logger.info("Writing results to csv file...");
        RouteAttributeWriter.write(trips, outputFile, calc.getAllAttributeNames());
    }

    private static LinkedHashMap<String,TravelAttribute> activeAttributes(String mode) {
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("vgvi",(l,td) -> LinkAttractiveness.getVgviFactor(l) * l.getLength());
        attributes.put("lighting",(l,td) -> LinkAttractiveness.getLightingFactor(l) * l.getLength());
        attributes.put("shannon", (l,td) -> LinkAttractiveness.getShannonFactor(l) * l.getLength());
        attributes.put("crime", (l,td) -> LinkAttractiveness.getCrimeFactor(l) * l.getLength());
        attributes.put("POIs",(l,td) -> LinkAttractiveness.getPoiFactor(l) * l.getLength());
        attributes.put("negPOIs",(l,td) -> LinkAttractiveness.getNegativePoiFactor(l) * l.getLength());
        attributes.put("freightPOIs",(l,td) -> LinkStress.getFreightPoiFactor(l) * l.getLength());
        attributes.put("attractiveness", (l,td) -> LinkAttractiveness.getDayAttractiveness(l) * l.getLength());
        attributes.put("stressLink",(l,td) -> LinkStress.getStress(l,mode) * l.getLength());
        attributes.put("stressJct",(l,td) -> JctStress.getJunctionStress(l,mode));
        return attributes;
    }
}
