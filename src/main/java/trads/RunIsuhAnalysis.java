package trads;

import resources.Properties;
import resources.Resources;
import routing.ActiveAttributes;
import routing.Bicycle;
import routing.TravelAttribute;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
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
import routing.disutility.JibeDisutility;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAttractiveness;
import routing.disutility.components.LinkStress;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.WalkTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;
import trads.io.RouteAttributeWriter;
import trads.io.TradsReader;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;

import static data.Place.*;

public class RunIsuhAnalysis {

    private final static Logger logger = Logger.getLogger(RunIsuhAnalysis.class);

    public static void main(String[] args) throws IOException {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output file");
        }

        Resources.initializeResources(args[0]);
        String outputFile = args[1];

        String boundaryFilePath = Resources.instance.getString(Properties.NETWORK_BOUNDARY);
        String networkFilePath = Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK);
        String transitScheduleFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_SCHEDULE);
        String transitNetworkFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_NETWORK);

        // Read network
        logger.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFilePath);

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
        Geometry boundary = GpkgReader.readBoundary(boundaryFilePath);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<TradsTrip> trips = TradsReader.readTrips(boundary);

        // Travel time
        FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        TravelTime ttBike = bicycle.getTravelTime();
        TravelTime ttWalk = new WalkTravelTime();

        // CALCULATOR
        TradsCalculator calc = new TradsCalculator(trips);

        // car (freespeed only)
        calc.network("car", HOME, DESTINATION, null, networkCar, carXy2l, freeSpeed, freeSpeed, null,false);

        // bike (jibe)
        calc.network("bike_jibe", HOME, DESTINATION, bike, networkBike, null, new JibeDisutility(TransportMode.bike,ttBike), ttBike, ActiveAttributes.get(TransportMode.bike),false);

        // walk (jibe)
        calc.network("walk_jibe", HOME, DESTINATION, null, networkWalk, null, new JibeDisutility(TransportMode.walk,ttWalk), ttWalk, ActiveAttributes.get(TransportMode.walk),false);

        // public transport
        calc.pt("pt", HOME, DESTINATION, config, transitScheduleFilePath, transitNetworkFilePath);

        // Write results
        logger.info("Writing results to csv file...");
        RouteAttributeWriter.write(trips, outputFile, calc.getAllAttributeNames());
    }
}
