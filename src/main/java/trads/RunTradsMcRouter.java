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
import trads.io.RoutePathWriter;
import trads.io.TradsReader;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static data.Place.DESTINATION;
import static data.Place.ORIGIN;

public class RunTradsMcRouter {

    private final static Logger logger = Logger.getLogger(RunTradsMcRouter.class);

    // Parameters for MC Simulation
    private final static int NUMBER_OF_SAMPLES = 10;
    private final static double MAX_MC_ATTRACTIVENESS = 3e-3; // based on avg travel time and rounded up todo: calculate for walking
    private final static double MAX_MC_STRESS = 3e-3; // based on average travel time and rounded up todo: calculate for walking
    private final static double MAX_JCT_M_EQUIVALENT = 20;

    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 3) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output File Path\n" +
                    "(2) Mode");
        }

        Resources.initializeResources(args[0]);
        String outputGpkg = args[1];
        String mode = args[2];

        String inputEdgesGpkg = Resources.instance.getString(Properties.NETWORK_LINKS);

        // Read network
        Network network = NetworkUtils2.readFullNetwork();

        // Create mode-specific networks
        logger.info("Creating " + mode + "-specific network...");
        Network modeSpecificNetwork = NetworkUtils2.extractModeSpecificNetwork(network, mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<TradsTrip> trips = TradsReader.readTrips(boundary);

        // Filter to only routable bike trips
        Set<TradsTrip> bikeTrips = trips.stream()
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

        // Constant marginal costs
        double mcTime = Resources.instance.getMarginalCost(mode,Properties.TIME);
        double mcDist = Resources.instance.getMarginalCost(mode,Properties.DISTANCE);
        double mcGrad = Resources.instance.getMarginalCost(mode,Properties.GRADIENT);
        double mcComfort = Resources.instance.getMarginalCost(mode,Properties.COMFORT);

        // CALCULATOR
        TradsCalculator calc = new TradsCalculator(bikeTrips);

        // Run short and fast routing (for reference)
        calc.network(mode + "_short", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new DistanceDisutility(), tt, ActiveAttributes.get(mode), true);
        calc.network(mode + "_fast", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new OnlyTimeDependentTravelDisutility(tt), tt, ActiveAttributes.get(mode), true);

        Random r = new Random();

        Counter counter = new Counter("Sampling route ", "/" + NUMBER_OF_SAMPLES);
        for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
            counter.incCounter();

            double mcAttr = r.nextDouble() * MAX_MC_ATTRACTIVENESS;
            double mcStress = r.nextDouble() * MAX_MC_STRESS;
            double mcJct = r.nextDouble() * MAX_MC_STRESS * MAX_JCT_M_EQUIVALENT;

            JibeDisutility disutilty = new JibeDisutility(mode, tt, mcTime, mcDist, mcGrad, mcComfort, mcAttr, mcStress, mcJct);

            calc.network(mode + "_jibe_" + i,ORIGIN,DESTINATION,veh,modeSpecificNetwork,modeSpecificNetwork,disutilty,tt,ActiveAttributes.getJibe(mode,veh),true);
        }

        // Write results
        logger.info("Writing results to gpkg file...");
        RoutePathWriter.write(bikeTrips, inputEdgesGpkg, outputGpkg, calc.getAllAttributeNames());
    }
}
