package trads;

import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import resources.Properties;
import resources.Resources;
import routing.disutility.DistanceDisutility;
import routing.travelTime.WalkTravelTime;
import trads.io.RouteAttributeWriter;
import trads.io.TradsReader;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static data.Place.DESTINATION;
import static data.Place.ORIGIN;

public class TradsPercentileCalculator {

    private final static Logger logger = Logger.getLogger(TradsPercentileCalculator.class);

    public static void main(String[] args) throws IOException {
        if(args.length == 0 || args.length > 2) {
            throw new RuntimeException("Program requires 1-2 arguments:\n" +
                    "(0) Properties file\n" +
                    "(1) OPTIONAL: output csv file");
        }

        Resources.initializeResources(args[0]);
        String outputCsv = args[1];
        double decayPercentile = Resources.instance.getDouble(Properties.DECAY_PERCENTILE);

        // Read network
        Network network = NetworkUtils2.readFullNetwork();

        // Create mode-specific networks
        logger.info("Creating bike-specific network...");
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Valid purpose pairs (based on TRADS data)
        TradsPurpose.PairList odPairs = new TradsPurpose.PairList();
        odPairs.addPair(TradsPurpose.HOME, TradsPurpose.SHOPPING_FOOD);
        odPairs.addPair(TradsPurpose.SHOPPING_FOOD, TradsPurpose.HOME);

//        Bicycle bicycle = new Bicycle(null);
//        TravelTime ttBike = bicycle.getTravelTime();

        double result = estimateBeta(TransportMode.walk, null, new WalkTravelTime(),
                new DistanceDisutility(),odPairs,networkWalk,networkWalk,boundary,outputCsv);
        double beta = getHansenBetaParameter(result, decayPercentile);

        logger.info("result = " + result);
        logger.info("hansen decay parameter = " + beta);
    }


    public static double estimateBeta(String mode, Vehicle vehicle, TravelTime travelTime, TravelDisutility travelDisutility,
                                      TradsPurpose.PairList purposePairs, Network network, Network xy2lNetwork,
                                      Geometry boundary, String outputCsvPath) throws IOException {

        double percentile = Resources.instance.getDouble(Properties.DECAY_PERCENTILE);
        logger.info("Calculating " + percentile + " percentile cost based on TRADS trips");

        // Read trips
        Set<TradsTrip> trips = TradsReader.readTrips(boundary);

        // Filter to trips meeting criteria
        Stream<TradsTrip> tripsStream = trips.stream().filter(t -> t.routable(ORIGIN,DESTINATION) && t.getMainMode().equals(mode));
        if(purposePairs != null) {
            tripsStream = tripsStream.filter(t -> purposePairs.contains(t.getStartPurpose(),t.getEndPurpose()));
        }
        Set<TradsTrip> validTrips = tripsStream.collect(Collectors.toSet());

        // Log results
        StringBuilder builder = new StringBuilder();
        builder.append("Identified ").append(validTrips.size()).append(" trips meeting the criteria: \n");
        builder.append("Mode: ").append(mode).append("\n");
        builder.append("Allowed purpose pairs: ");
        if(purposePairs != null) {
            for(TradsPurpose.Pair pair : purposePairs.getList()){
                builder.append("\n").append("START: ").append(pair.getStartPurpose().toString());
                builder.append("\n").append("  END: ").append(pair.getEndPurpose().toString());
            }
        } else {
            builder.append("(ALL)");
        }
        logger.info(builder.toString());

        // Route trips
        TradsCalculator calc = new TradsCalculator(validTrips);
        calc.network("pc",ORIGIN,DESTINATION,vehicle,network,xy2lNetwork,travelDisutility,travelTime,null,false);

        // Get sorted array of costs
        double[] costs = validTrips.stream().mapToDouble(t -> (double) t.getAttribute("pc", "cost")).sorted().toArray();

        // Write outputs
        if(outputCsvPath != null) {
            RouteAttributeWriter.write(validTrips,outputCsvPath,calc.getAllAttributeNames());
        }

        // Calculate percentile
        double percentileValue = getPercentile(costs,percentile);
        logger.info("Value at percentile " + percentile + " = " + percentileValue);

        // Calculate beta parameter
        double beta = getHansenBetaParameter(percentileValue,percentile);
        logger.info("Beta parameter = " + beta);

        return beta;
    }

    private static double getPercentile(double[] x, double percentile) {
        if(percentile < 0 || percentile > 1) {
            throw new RuntimeException("Percentile must be between 0 and 1");
        } else {
            int n = x.length;
            double index = 1 + Math.max(n-1,0) * percentile;
            int lo = (int) Math.floor(index);
            int hi = (int) Math.ceil(index);
            double qs = x[lo - 1];
            if(index > lo && x[hi-1] != qs) {
                double h = index - lo;
                qs = (1 - h) * qs + h * x[hi-1];
            }
            return qs;
        }
    }

    private static double getHansenBetaParameter(double value, double percentile) {
        return -1 * Math.log(1-percentile) / value;
    }

}
