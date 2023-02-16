package census;

import gis.ShpReader;
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
import resources.Properties;
import resources.Resources;
import routing.Bicycle;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;
import trads.TradsCalculator;
import trip.Trip;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static trip.Place.*;

public class RunCensusMcRouter {

    private final static Logger logger = Logger.getLogger(RunCensusMcRouter.class);

    // Parameters for MC Simulation
    private final static double MAX_MC_ATTRACTIVENESS = 5e-3; // based on avg travel time and rounded up
    private final static double MAX_MC_STRESS = 5e-3; // based on average travel time and rounded up
    private final static double MAX_JCT_M_EQUIVALENT = 20;

    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Properties file\n" +
                    "(1) MSOAs Shapefile\n" +
                    "(2) Census matrix\n" +
                    "(3) Output file path\n" +
                    "(4) Number of samples");
        }

        Resources.initializeResources(args[0]);

        String zonesFile = args[1];
        String censusMatrix = args[2];
        String outputCsv = args[3];
        int numberOfSamples = Integer.parseInt(args[4]);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Map<String, Geometry> MSOAs = ShpReader.readZones(zonesFile,"MSOA11CD");
        Set<Trip> trips = CensusReader.readAndProcessMatrix(MSOAs,censusMatrix,1.);

        // Get mode
        String mode = trips.iterator().next().getMainMode();
        logger.info("Identified mode " + mode + " from census data.");

        // Read network
        Network network = NetworkUtils2.readFullNetwork();

        // Create mode-specific networks
        logger.info("Creating " + mode + "-specific network...");
        Network modeSpecificNetwork = NetworkUtils2.extractModeSpecificNetwork(network, mode);

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
        TradsCalculator calc = new TradsCalculator(trips);

        // Run short and fast routing (for reference)
        calc.network(mode + "_short", HOME, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new DistanceDisutility(), tt, null, false);
        calc.network(mode + "_fast", HOME, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new OnlyTimeDependentTravelDisutility(tt), tt, null, false);

        Random r = new Random();

        Counter counter = new Counter("Sampling route ", "/" + numberOfSamples);
        for (int i = 0; i < numberOfSamples; i++) {
            counter.incCounter();

            double mcAttr = r.nextDouble() * MAX_MC_ATTRACTIVENESS;
            double mcStress = r.nextDouble() * MAX_MC_STRESS;
            double mcJct = r.nextDouble() * MAX_MC_STRESS * MAX_JCT_M_EQUIVALENT;

            JibeDisutility disutilty = new JibeDisutility(mode, tt, mcTime, mcDist, mcGrad, mcComfort, mcAttr, mcStress, mcJct);

            calc.network(mode + "_jibe_" + i,HOME,DESTINATION,veh,modeSpecificNetwork,modeSpecificNetwork,disutilty,tt,null,false);
        }

        // Write results
        logger.info("Writing results to csv file...");
        CensusWriter.write(trips,outputCsv,calc.getAllAttributeNames());
    }
}
