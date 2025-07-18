package estimation;

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

    public static void run(String[] args, ModelLoader model, boolean computeRouteData,
                           String idVar, DiaryReader.IdMatcher idMatcher) throws IOException, FactoryException {

        if(args.length < 3 || args.length > 4) {
            throw new RuntimeException("""
                    Program requires 3-4 arguments:\s
                    (0) Properties file\s
                    (1) Logit data file\s
                    (2) Estimation results file\s
                    (3) [OPTIONAL] Predicted output probabilities file""");
        }

        Resources.initializeResources(args[0]);
        String logitDataFilepath = args[1];
        String estimationResultPath = args[2];
        boolean runPrediction = args.length == 4;


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
        Network networkBike = null;
        Network networkWalk = null;
        TravelTime ttBike = null;
        TravelTime ttWalk = null;
        Vehicle bike = null;

        if(computeRouteData || runPrediction) {

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
            Network network = NetworkUtils2.readFullNetwork();
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
            // Run estimation
            logger.info("No estimation results CSV file found. Running estimation...");
            MultinomialLogit.run(u,y,k,0,1e-10,500,estimationResultPath);
            logger.info("finished estimation.");
        } else {
            // Don't run estimation
            logger.info("Estimation results CSV file already exists. Skipping to prediction...");
        }

        // Prediction
        if(runPrediction) {
            String predictionOutputFilepath = args[3];

            // Run prediction
            logger.info("Running prediction...");
            double[] coefficients = CoefficientsIO.read(u,resultsFile);

            // Create vector of variable coefficients only
            double[] varCoefficients = u.contractCoeffs(coefficients);

            if(u.getDynamicComponent() != null) {
                u.getDynamicComponent().update(varCoefficients);
            }

            // Compute LL
            MultinomialLogitObjective objective = new MultinomialLogitObjective(u, y, k, 0.);
            logger.info("Computed LL = " + objective.f(varCoefficients));

            // Compute probabilities
            double[][] prediction = objective.p(coefficients);

            // Write results
            PrintWriter out = ioUtils.openFileForSequentialWriting(new File(predictionOutputFilepath), false);
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
            for(int i = 0 ; i < y.length ; i++) {
                Trip trip = tripList.get(i);
                builder = new StringBuilder();
                builder.append(ids.get(i)).
                        append(SEP).append(trip.getHouseholdId()).
                        append(SEP).append(trip.getPersonId()).
                        append(SEP).append(trip.getTripId());

                for(int j  = 0 ; j < k ; j++) {
                    builder.append(SEP).append(prediction[i][j]);
                }
                out.println(builder);
            }

            out.close();
        }
    }
}
