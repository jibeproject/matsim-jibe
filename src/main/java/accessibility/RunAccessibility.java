package accessibility;

import accessibility.decay.DecayFunction;
import accessibility.decay.Hansen;
import accessibility.decay.Isochrone;
import accessibility.resources.AccessibilityProperties;
import accessibility.resources.AccessibilityResources;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import resources.Resources;

import org.apache.log4j.Logger;
import trads.TradsPercentileCalculator;
import trads.TradsPurpose;

import java.io.IOException;
import java.util.Set;

public class RunAccessibility {

    public static final Logger log = Logger.getLogger(RunAccessibility.class);
    private static Network fullNetwork;
    private static Geometry regionBoundary;
    private static Geometry networkBoundary;


    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            throw new RuntimeException("Program requires at least 2 arguments: \n" +
                    "(0) General Properties file\n" +
                    "(1+) Accessibility properties file(s) \n");
        }

        Resources.initializeResources(args[0]);

        // Read network
        fullNetwork = NetworkUtils2.readFullNetwork();

        // Read boundary shapefiles
        log.info("Reading boundary shapefiles...");
        regionBoundary = GpkgReader.readRegionBoundary();
        networkBoundary = GpkgReader.readNetworkBoundary();

        // Check that origin boundary is within destination boundary
        if(!regionBoundary.within(networkBoundary)) {
            throw new RuntimeException("Region boundary must be within network boundary!");
        }

        // Loop through and calculate
        for(int i = 1 ; i < args.length ; i++) {
            runAnalysis(args[i]);
        }
    }

    private static void runAnalysis(String accessibilityProperties) throws IOException {
        log.info("Running analysis for " + accessibilityProperties);

        AccessibilityResources.initializeResources(accessibilityProperties);

        // Mode
        String mode = AccessibilityResources.instance.getMode();

        // Create mode-specific network
        Network network = NetworkUtils2.extractModeSpecificNetwork(fullNetwork,mode);

        // Travel time, vehicle, disutility
        TravelTime tt = AccessibilityResources.instance.getTravelTime();
        Vehicle veh = AccessibilityResources.instance.getVehicle();
        TravelDisutility td = AccessibilityResources.instance.getTravelDisutility();

        // Decay function
        String decayType = AccessibilityResources.instance.getString(AccessibilityProperties.DECAY_FUNCTION);
        double cutoffTime = AccessibilityResources.instance.getDouble(AccessibilityProperties.CUTOFF_TIME);
        double cutoffDist = AccessibilityResources.instance.getDouble(AccessibilityProperties.CUTOFF_DISTANCE);
        double beta = AccessibilityResources.instance.getDouble(AccessibilityProperties.BETA);

        DecayFunction df;
        if (decayType.equalsIgnoreCase("hansen")) {
            if(Double.isNaN(beta)) {
                log.info("Hansen accessibility desired but no beta value given. Estimating beta from TRADS survey");
                TradsPurpose.PairList includedPurposePairs = AccessibilityResources.instance.getPurposePairs();
                String outputCsv = AccessibilityResources.instance.getString(AccessibilityProperties.TRADS_OUTPUT_CSV);
                beta = TradsPercentileCalculator.estimateBeta(mode,veh,tt,td,includedPurposePairs,
                        network,network,networkBoundary,outputCsv);
            }
            df = new Hansen(beta,cutoffTime,cutoffDist);
        } else if (decayType.equalsIgnoreCase("isochrone")) {
            df = new Isochrone(cutoffTime, cutoffDist);
        } else {
            throw new RuntimeException("Do not recognise decay function type \"" + decayType + "\"");
        }
        log.info("Initialised " + decayType + " decay function with the following parameters:" +
                 "\nBeta: " +  (decayType.equalsIgnoreCase("hansen") ? beta : "N/A") +
                 "\nTime cutoff (seconds): " + cutoffTime +
                 "\nDistance cutoff (meters): " + cutoffDist);

        // Destination data
        String destinationFileName = AccessibilityResources.instance.getString(AccessibilityProperties.DESTINATIONS);
        String outputFileName = AccessibilityResources.instance.getString(AccessibilityProperties.OUTPUT);
        if(destinationFileName == null) {
            log.warn("No destination information given. Skipping accessibility calculation.");
            return;
        } else if (outputFileName == null) {
            log.warn("No output filename given. Skipping accessibility calculation.");
            return;
        }
        DestinationData destinations = new DestinationData(destinationFileName,networkBoundary);

        // Origin nodes
        log.info("Identifying origin nodes within boundary...");
        Set<Node> originNodes = NetworkUtils2.getNodesInBoundary(network,regionBoundary);

        // Accessibility calculation
        log.info("Running accessibility calculation...");
        long startTime = System.currentTimeMillis();
        IdMap<Node,Double> accessibilities = NodeCalculator.calculate(
                network, originNodes, destinations.getNodes(network,network), destinations.getWeights(), tt, td, veh, df);
        long endTime = System.currentTimeMillis();
        log.info("Calculation time: " + (endTime - startTime));

        // Write results as gpkg
        AccessibilityWriter.writeNodesAsGpkg(accessibilities,fullNetwork,outputFileName);
    }
}
