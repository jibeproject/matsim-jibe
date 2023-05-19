package trads;

import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import routing.Bicycle;
import routing.travelTime.WalkTravelTime;
import trads.calculate.MultiRouteCalculator;
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trads.io.TradsRouteWriter;
import trip.Trip;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class RunTradsKRouter {

    private final static Logger logger = Logger.getLogger(RunTradsKRouter.class);

    public static void main(String[] args) throws IOException, FactoryException {
        if(args.length != 4) {
            throw new RuntimeException("Program requires 4 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output csv file path \n" +
                    "(2) Output gpkg file path \n" +
                    "(3) Mode");
        }

        Resources.initializeResources(args[0]);
        String outputCsv = args[1];
        String outputGpkg = args[2];
        String mode = args[3];

        String boundaryFilePath = Resources.instance.getString(Properties.NETWORK_BOUNDARY);

        // Read network
        Network modeNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readBoundary(boundaryFilePath);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);

        // Filter to only routable bike/walk trips
        Set<Trip> tripsByMode = trips.stream()
                .filter(t -> t.routable(ORIGIN,DESTINATION) && t.getMainMode().equals(mode)).limit(20)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
        MultiRouteCalculator.calculate(tripsByMode, ORIGIN, DESTINATION, modeNetwork, modeNetwork, tt, veh,1.15);

        // Write results to combined CSV
        TradsCsvWriter.write(tripsByMode, outputCsv,
                Set.of("dist","time","vgvi","shannon","POIs","negPOIs","crime","linkStress","jctStress"));

        // Write results to multiple GPKGs (one per route)
        for(Trip trip : tripsByMode) {
            String fileName = trip.getHouseholdId() + "_" + trip.getPersonId() + "_" + trip.getTripId() + ".gpkg";
            TradsRouteWriter.write(Set.of(trip),outputGpkg + "/" + fileName,
                    Set.of("dist","time","vgvi","shannon","POIs","negPOIs","crime","linkStress","jctStress"));
        }
    }
}
