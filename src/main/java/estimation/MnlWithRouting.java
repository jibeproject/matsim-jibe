package estimation;

import estimation.dynamic.DynamicActive;
import estimation.specifications.AbstractModelSpecification;
import gis.GpkgReader;
import io.DiaryReader;
import io.ioUtils;
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
import smile.classification.ClassLabels;
import trip.Trip;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class MnlWithRouting {

    private final static Logger logger = Logger.getLogger(MnlWithRouting.class);
    private static final String SEP = ",";


    public interface ModelLoader {
        AbstractModelSpecification load(LogitData data, Trip[] trips,
                                        Network netBike, Vehicle vehBike, TravelTime ttBike,
                                        Network netWalk, Vehicle vehWalk, TravelTime ttWalk);

    }

    public static String getOptionalArg(String[] args, String identifier) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(identifier))
                return args[i + 1];
        }
        return null;
    }

    public static void run(String[] args, ModelLoader model, boolean computeRouteData,
                           String idVar, DiaryReader.IdMatcher idMatcher) throws IOException, FactoryException {

        if(args.length < 3 || args.length > 7) {
            throw new RuntimeException("""
                    Program requires 3-6 arguments:\s
                    (0) Properties file\s
                    (1) Logit data file\s
                    (2) Estimation results file\s
                    (3) [optional with -s] Starting values from a previous run (variable names must match)\s
                    (4) [optional with -p] Predicted output probabilities file\s
                    (5) [optional with -g] Predicted output marginal generalised costs\s
                    (6) [optional with -r] Output routes filename (.gpkg)""");
        }

        Resources.initializeResources(args[0]);
        String logitDataFilepath = args[1];
        String estimationResultPath = args[2];
        String startingValuesPath = getOptionalArg(args, "-s");
        String predictedProbabilitiesFile = getOptionalArg(args, "-p");
        String predictedMarginalCostsFile = getOptionalArg(args, "-g");
        String routesOutputFile = getOptionalArg(args, "-r");

        boolean predictProbabilities = predictedProbabilitiesFile  != null;
        boolean predictMarginalCosts = predictedMarginalCostsFile != null;
        boolean printRoutes = routesOutputFile != null;
        logger.info("Running prediction: " + predictProbabilities);

        // Read in TRADS trips from CSV
        logger.info("Reading fixed input data from ascii file...");
        LogitData logitData = new LogitData(logitDataFilepath,"choice",idVar);
        logitData.read();

        // Organise classes
        int[] y = logitData.getChoices();
        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        y = codec.y;
        System.out.println("Identified " + k + " classes.");

        // Declare utility specification
        AbstractModelSpecification u;

        Trip[] trip_data = null;
        List<Trip> tripList = null;
        List<String> ids;
        Network network = null;
        Network networkBike = null;
        Network networkWalk = null;
        TravelTime ttBike = null;
        TravelTime ttWalk = null;
        Vehicle bike = null;

        if(computeRouteData || predictProbabilities || predictMarginalCosts) {

            // Read Boundary Shapefile
            logger.info("Reading boundary shapefile...");
            Geometry boundary = GpkgReader.readNetworkBoundary();

            // Read in diary file
            logger.info("Reading person micro data from ascii file...");
            ids = List.of(logitData.getIds());
            Set<Trip> trips = DiaryReader.readTrips(boundary,
                    (hhid,pid,tid) -> ids.contains(idMatcher.combine(hhid,pid,tid)));

            // Organise diary trips
            trip_data = new Trip[ids.size()];
            for(Trip trip : trips) {
                String combinedId = idMatcher.combine(trip.getHouseholdId(),trip.getPersonId(),trip.getTripId());
                int i = ids.indexOf(combinedId);
                if(i > -1) {
                    trip_data[i] = trip;
                }
            }

            // check all records are attached to a trip (also ensures none of them are null)
            tripList = List.of(trip_data);
        } else {
            ids = null;
        }

        // Network & travel time (only if computing route data)
        if(computeRouteData) {

            // Read network
            network = NetworkUtils2.readFullNetwork();
            networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);
            networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

            // Travel Time
            Bicycle bicycle = new Bicycle(null);
            bike = bicycle.getVehicle();
            ttBike = bicycle.getTravelTimeFast(networkBike);
            ttWalk = new WalkTravelTime();
        }

        // Initialise utility specification
        u = model.load(logitData,trip_data,networkBike,bike,ttBike,networkWalk,null,ttWalk);

        // Estimation
        File resultsFile = new File(estimationResultPath + ".csv");
        if(!resultsFile.exists()) {
            // Override starting values
            if(startingValuesPath != null) {
                File startingValuesFile = new File(startingValuesPath);
                if (!startingValuesFile.exists()) {
                    throw new RuntimeException("Starting values file not found! " + startingValuesPath);
                } else {
                    logger.warn("Overriding starting values with values from " + startingValuesPath);
                    double[] startingValues = CoefficientsIO.read(u,new File(startingValuesPath),false);
                    u.overrideStarting(startingValues);
                    if(u.getDynamicComponent() != null) {
                        u.getDynamicComponent().update(u.contractCoeffs(startingValues));
                    }
                }
            }

            // Run estimation
            logger.info("No estimation results CSV file found. Running estimation...");
            MultinomialLogit.run(u,y,k,0,1e-10,500,estimationResultPath);
            logger.info("finished estimation.");
        } else {
            // Don't run estimation
            logger.info("Estimation results CSV file already exists. Skipping estimation...");
        }

        // Read in coefficients for prediction or route writing
        if(predictProbabilities || predictMarginalCosts || printRoutes) {

            // Read coefficients and store into dynamic component
            double[] coefficients = CoefficientsIO.read(u,resultsFile,true);

            // Create vector of variable coefficients only
            double[] varCoefficients = u.contractCoeffs(coefficients);

            if(u.getDynamicComponent() != null) {
                u.getDynamicComponent().update(varCoefficients);
            }

            if(predictProbabilities || predictMarginalCosts) {

                logger.info("Predicting probabilities...");

                // Compute LL
                MultinomialLogitObjective objective = new MultinomialLogitObjective(u, y, k, 0.);
                logger.info("Computed LL = " + objective.f(varCoefficients));

                // Compute probabilities
                if (predictProbabilities) {
                    double[][] probabilities = objective.p(coefficients);
                    writePrediction(predictedProbabilitiesFile, u, y, tripList, ids, k, probabilities);
                }

                // Compute generalised costs
                if (predictMarginalCosts) {
                    double[][] marginalCosts = objective.mc(coefficients);
                    writePrediction(predictedMarginalCostsFile, u, y, tripList, ids, k, marginalCosts);
                }
            }

            if(printRoutes) {
                ((DynamicActive) u.getDynamicComponent()).mapRoutes(routesOutputFile, logitData.getIds(), network);
            }

        }
    }

    private static void writePrediction(String predictionOutputFile, AbstractModelSpecification u, int[] y,
                                        List<Trip> tripList, List<String> ids, int k, double[][] prediction) {
        // Write results
        logger.info("Writing prediction results to " + predictionOutputFile);
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(predictionOutputFile), false);
        assert out != null;

        // Print header
        StringBuilder builder = new StringBuilder();
        builder.append("ID")
                .append(SEP).append(Resources.instance.getString(Properties.HOUSEHOLD_ID))
                .append(SEP).append(Resources.instance.getString(Properties.PERSON_ID))
                .append(SEP).append(Resources.instance.getString(Properties.TRIP_ID));
        for(String mode : u.getChoiceNames()) {
            builder.append(SEP).append(mode);
        }
        out.println(builder);

        // Print each line
        for(int i = 0; i < y.length ; i++) {
            Trip trip = tripList.get(i);
            builder = new StringBuilder();
            builder.append(ids.get(i)).
                    append(SEP).append(trip.getHouseholdId()).
                    append(SEP).append(trip.getPersonId()).
                    append(SEP).append(trip.getTripId());

            for(int j = 0; j < k; j++) {
                builder.append(SEP).append(prediction[i][j]);
            }
            out.println(builder);
        }

        out.close();
    }
}
