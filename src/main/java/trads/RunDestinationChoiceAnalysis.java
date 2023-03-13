package trads;

import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import resources.Resources;
import routing.disutility.DistanceDisutility;
import routing.travelTime.WalkTravelTime;
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trip.Trip;

import java.io.IOException;
import java.util.Set;

import static trip.Place.*;

// SCRIPT TO ADD MAIN DISTANCES FOR ANALYZING RUBBER BANDING IN TOUR-MAKING

public class RunDestinationChoiceAnalysis {

    private final static Logger logger = Logger.getLogger(RunDestinationChoiceAnalysis.class);

    public static void main(String[] args) throws IOException {

        if(args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output file path");
        }

        Resources.initializeResources(args[0]);
        String outputFile = args[1];

        // Read network
        Network network = NetworkUtils2.readFullNetwork();

        // Create mode-specific networks
        logger.info("Creating walk network...");
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);

        // Travel time
        TravelTime ttWalk = new WalkTravelTime();

        // CALCULATOR
        TradsCalculator calc = new TradsCalculator(trips);

        // beeline
        calc.beeline("home_beeline",HOME, DESTINATION);
        calc.beeline("main_beeline",MAIN, DESTINATION);

        // network distances (based on walk network)
        calc.network("home", HOME, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, null,false);
        calc.network("main", MAIN, DESTINATION, null, networkWalk, null, new DistanceDisutility(), ttWalk, null,false);

        // Write results
        logger.info("Writing results to csv file...");
        TradsCsvWriter.write(trips, outputFile, calc.getAllAttributeNames());
    }
}
