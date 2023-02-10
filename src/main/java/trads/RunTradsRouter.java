package trads;

import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
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
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.opengis.referencing.FactoryException;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAttractiveness;
import routing.disutility.components.LinkStress;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.WalkTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;
import trads.io.RoutePathWriter;
import trads.io.TradsReader;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

import static data.Place.*;
import static data.Place.DESTINATION;

public class RunTradsRouter {

    private final static Logger logger = Logger.getLogger(RunTradsRouter.class);

    public static void main(String[] args) throws IOException, FactoryException {
        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output File Path \n" +
                    "(2) Mode");
        }

        Resources.initializeResources(args[0]);
        String outputGpkg = args[1];
        String mode = args[2];

        String boundaryFilePath = Resources.instance.getString(Properties.NETWORK_BOUNDARY);
        String networkFilePath = Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK);
        String inputEdgesGpkg = Resources.instance.getString(Properties.NETWORK_LINKS);

        // Read network
        logger.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFilePath);

        // Create mode-specific networks
        logger.info("Creating " + mode + "-specific network...");
        Network modeSpecificNetwork = NetworkUtils2.extractModeSpecificNetwork(network, mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readBoundary(boundaryFilePath);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<TradsTrip> trips = TradsReader.readTrips(boundary);

        // Filter to only routable bike/walk trips
        Set<TradsTrip> tripsByMode = trips.stream()
                .filter(t -> t.routable(ORIGIN,DESTINATION) && t.getMainMode().equals(mode))
                .collect(Collectors.toSet());
        logger.info("Identified " + tripsByMode.size() + " " + mode + " trips.");

        // Travel time and vehicle
        TravelTime tt;
        Vehicle veh;

        if(mode.equals(TransportMode.bike)) {
            Bicycle bicycle = new Bicycle(null);
            tt = bicycle.getTravelTime();
            veh = bicycle.getVehicle();
        } else if (mode.equals(TransportMode.walk)) {
            tt = new WalkTravelTime();
            veh = null;
        } else throw new RuntimeException("Modes other than walk and bike are not supported!");

        // Calculate shortest, fastest, and jibe route
        TradsCalculator calc = new TradsCalculator(tripsByMode);
        calc.network(mode + "_short", ORIGIN, DESTINATION,  veh, modeSpecificNetwork, modeSpecificNetwork, new DistanceDisutility(), tt, ActiveAttributes.get(mode),true);
        calc.network(mode + "_fast", ORIGIN, DESTINATION,  veh, modeSpecificNetwork, modeSpecificNetwork, new OnlyTimeDependentTravelDisutility(tt), tt, ActiveAttributes.get(mode),true);
        calc.network(mode + "_jibe", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new JibeDisutility(mode,tt), tt, ActiveAttributes.get(mode),true);

        // Write results
        logger.info("Writing results to gpkg file...");
        RoutePathWriter.write(tripsByMode, inputEdgesGpkg, outputGpkg, calc.getAllAttributeNames());
    }
}
