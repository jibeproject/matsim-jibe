package trads;

import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import resources.Properties;
import resources.Resources;
import routing.ActiveAttributes;
import routing.Bicycle;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;
import trads.io.TradsRouteWriter;
import trads.io.TradsReader;
import trip.Trip;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static trip.Place.*;
import static trip.Place.DESTINATION;

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
        calc.network(mode + "_short", ORIGIN, DESTINATION,  veh, modeNetwork, modeNetwork, new DistanceDisutility(), tt, ActiveAttributes.get(mode),true);
        calc.network(mode + "_fast", ORIGIN, DESTINATION,  veh, modeNetwork, modeNetwork, new OnlyTimeDependentTravelDisutility(tt), tt, ActiveAttributes.get(mode),true);
        calc.network(mode + "_jibe", ORIGIN, DESTINATION, veh, modeNetwork, modeNetwork, new JibeDisutility(mode,tt), tt, ActiveAttributes.get(mode),true);

        // Write results
        logger.info("Writing results to gpkg file...");
        TradsRouteWriter.write(tripsByMode, outputGpkg, calc.getAllAttributeNames());
    }
}
