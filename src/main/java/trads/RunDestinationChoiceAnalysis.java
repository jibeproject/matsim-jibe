package trads;

import network.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelTime;
import routing.disutility.DistanceDisutility;
import routing.travelTime.WalkTravelTime;

import java.io.IOException;
import java.util.Set;

import static data.Place.*;

// SCRIPT TO ADD MAIN DISTANCES FOR ANALYZING RUBBER BANDING IN TOUR-MAKING

public class RunDestinationChoiceAnalysis {

    private final static Logger logger = Logger.getLogger(RunDestinationChoiceAnalysis.class);

    public static void main(String[] args) throws IOException {

        if(args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Survey File Path \n" +
                    "(1) Boundary Geopackage Path \n" +
                    "(2) Network File Path \n" +
                    "(3) Output File Path \n" +
                    "(4) Number of Threads \n");
        }

        String surveyFilePath = args[0];
        String boundaryFilePath = args[1];
        String networkFilePath = args[2];
        String outputFile = args[3];
        int numberOfThreads = Integer.parseInt(args[4]);

        // Read network
        logger.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFilePath);

        // Create mode-specific networks
        logger.info("Creating walk network...");
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readBoundary(boundaryFilePath);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<TradsTrip> trips = TradsIo.readTrips(surveyFilePath, boundary);

        // Travel time
        TravelTime ttWalk = new WalkTravelTime();

        // Calculate network indicators
        logger.info("Calculating network indicators using " + numberOfThreads + " threads.");

        // CALCULATOR
        TradsCalculator calc = new TradsCalculator(10, trips);

        // beeline
        calc.beeline("home_beeline",HOME, DESTINATION);
        calc.beeline("main_beeline",MAIN, DESTINATION);

        // network distances (based on walk network)
        calc.network("home", HOME, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, null);
        calc.network("main", MAIN, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, null);

        // Write results
        logger.info("Writing results to csv file...");
        TradsIo.writeIndicators(trips, outputFile, calc.getAllAttributeNames());
    }
}
