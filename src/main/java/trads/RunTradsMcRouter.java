package trads;

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
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trads.io.TradsUniqueRouteWriter;
import trip.Trip;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class RunTradsMcRouter {

    private final static Logger logger = Logger.getLogger(RunTradsMcRouter.class);

    // Parameters for MC Simulation
    private final static double MAX_MC_AMBIENCE = 5e-3; // based on avg travel time and rounded up
    private final static double MAX_MC_STRESS = 5e-3; // based on average travel time and rounded up

    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 5) {
            throw new RuntimeException("Program requires 4 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output gpkg file path\n" +
                    "(2) Output csv file path\n" +
                    "(3) Mode\n" +
                    "(4) Number of samples");
        }

        Resources.initializeResources(args[0]);
        String outputGpkg = args[1];
        String outputCsv = args[2];
        String mode = args[3];
        int numberOfSamples = Integer.parseInt(args[4]);

        String inputEdgesGpkg = Resources.instance.getString(Properties.NETWORK_LINKS);

        // Read network
        Network modeSpecificNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);

        // Filter to only routable trips with chosen mode
        Set<Trip> selectedTrips = trips.stream()
                .filter(t -> t.routable(ORIGIN, DESTINATION) && t.getMainMode().equals(mode))
                .collect(Collectors.toSet());

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

        // CALCULATOR
        RouteIndicatorCalculator calc = new RouteIndicatorCalculator(selectedTrips);

        // Run short and fast routing (for reference)
        calc.network("short", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new DistanceDisutility(), tt, ActiveAttributes.get(mode), true);
        calc.network("fast", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new OnlyTimeDependentTravelDisutility(tt), tt, ActiveAttributes.get(mode), true);
        calc.network("jibe",ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new JibeDisutility(mode,tt), tt, ActiveAttributes.getJibe(mode,veh), true);

        Random r = new Random();

        Counter counter = new Counter("Sampling route ", "/" + numberOfSamples);
        for (int i = 0; i < numberOfSamples; i++) {
            counter.incCounter();

            double mcAttr = r.nextDouble() * MAX_MC_AMBIENCE;
            double mcStress = r.nextDouble() * MAX_MC_STRESS;

            JibeDisutility disutilty = new JibeDisutility(mode, tt, mcAttr, mcStress);

            calc.network("jibe_" + i,ORIGIN,DESTINATION,veh,modeSpecificNetwork,modeSpecificNetwork,disutilty,tt,ActiveAttributes.getJibe(mode,veh),true);
        }

        // Write results
        logger.info("Writing results to gpkg file...");
        TradsUniqueRouteWriter.write(selectedTrips, inputEdgesGpkg, outputGpkg);
        TradsCsvWriter.write(selectedTrips,outputCsv,calc.getAllAttributeNames());

    }
}
