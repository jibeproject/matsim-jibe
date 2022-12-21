package trads;

import ch.sbb.matsim.analysis.TravelAttribute;
import network.GpkgReader;
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
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
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
    private final static double MAX_BIKE_SPEED = 16 / 3.6;

    public static void main(String[] args) throws IOException, FactoryException {
        if(args.length != 6) {
            throw new RuntimeException("Program requires 7 arguments: \n" +
                    "(0) Survey File Path \n" +
                    "(1) Boundary Geopackage Path \n" +
                    "(2) Network File Path \n" +
                    "(3) Input Network Edges File \n" +
                    "(4) Output File Path \n" +
                    "(5) Number of Threads \n");
        }

        String surveyFilePath = args[0];
        String boundaryFilePath = args[1];
        String networkFilePath = args[2];
        String inputEdgesGpkg = args[3];
        String outputGpkg = args[4];
        int numberOfThreads = Integer.parseInt(args[5]);


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
        logger.info("Creating bike-specific network...");
        Network networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readBoundary(boundaryFilePath);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<TradsTrip> trips = TradsReader.readTrips(surveyFilePath, boundary);

        // Filter to only routable bike trips
        Set<TradsTrip> bikeTrips = trips.stream()
                .filter(t -> t.routable(ORIGIN,DESTINATION) && t.getMainMode().equals("Bicycle"))
                .collect(Collectors.toSet());

        // Limit to first N records (for debugging only)
//        trips = trips.stream().limit(200).collect(Collectors.toSet());

        // Travel time
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        TravelTime ttBike = new BicycleTravelTime(linkSpeedCalculator);

        // Calculate network indicators
        logger.info("Calculating routes using " + numberOfThreads + " threads.");

        // CALCULATOR
        TradsCalculator calc = new TradsCalculator(numberOfThreads, bikeTrips);

        // bike (shortest, fastest, and jibe)
        calc.network("bike_short", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new DistanceDisutility(), ttBike, activeAttributes(TransportMode.bike),true);
        calc.network("bike_fast", ORIGIN, DESTINATION,  bike, networkBike, networkBike, new OnlyTimeDependentTravelDisutility(ttBike), ttBike, activeAttributes(TransportMode.bike),true);
        calc.network("bike_jibe", ORIGIN, DESTINATION, bike, networkBike, networkBike, new JibeDisutility(TransportMode.bike,ttBike), ttBike, activeAttributes(TransportMode.bike),true);

        // Write results
        logger.info("Writing results to gpkg file...");
        RoutePathWriter.write(bikeTrips, inputEdgesGpkg, outputGpkg, calc.getAllAttributeNames());
    }

    private static LinkedHashMap<String, TravelAttribute> activeAttributes(String mode) {
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
