package accessibility;

import accessibility.decay.DecayFunction;
import accessibility.decay.Hansen;
import accessibility.decay.Isochrone;
import accessibility.resources.AccessibilityProperties;
import accessibility.resources.AccessibilityResources;
import gis.GisUtils;
import gis.GpkgReader;
import gis.grid.GridData;
import network.NetworkUtils2;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import resources.Resources;

import org.apache.log4j.Logger;
import trads.PercentileCalculator;
import trip.Purpose;

import java.io.IOException;
import java.util.Map;
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

        // Inputs/outputs
        String destinationFileName = AccessibilityResources.instance.getString(AccessibilityProperties.DESTINATIONS);
        String outputNodesFileName = AccessibilityResources.instance.getString(AccessibilityProperties.NODE_OUTPUT);
        String inputGridFileName = AccessibilityResources.instance.getString(AccessibilityProperties.GRID_INPUT);
        String outputGridFileName = AccessibilityResources.instance.getString(AccessibilityProperties.GRID_OUTPUT);

        // Decay function
        String decayType = AccessibilityResources.instance.getString(AccessibilityProperties.DECAY_FUNCTION);
        double cutoffTime = AccessibilityResources.instance.getDouble(AccessibilityProperties.CUTOFF_TIME);
        double cutoffDist = AccessibilityResources.instance.getDouble(AccessibilityProperties.CUTOFF_DISTANCE);
        double beta = AccessibilityResources.instance.getDouble(AccessibilityProperties.BETA);

        DecayFunction df;
        if (decayType.equalsIgnoreCase("hansen")) {
            if(Double.isNaN(beta)) {
                log.info("Hansen accessibility desired but no beta value given. Estimating beta from TRADS survey");
                Purpose.PairList includedPurposePairs = AccessibilityResources.instance.getPurposePairs();
                String outputCsv = AccessibilityResources.instance.getString(AccessibilityProperties.TRADS_OUTPUT_CSV);
                beta = PercentileCalculator.estimateBeta(mode,veh,tt,td,includedPurposePairs,
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

        // Checks on whether to perform NODE calculation
        if(destinationFileName == null) {
            log.warn("No destination file given. Skipping all accessibility calculations.");
            return;
        }
        if (outputNodesFileName == null && (inputGridFileName == null || outputGridFileName == null)) {
            log.warn("No node output or grid input/output files given. Skipping all accessibility calculations.");
            return;
        }
        DestinationData destinations = new DestinationData(destinationFileName,networkBoundary);
        Map<String, IdSet<Node>> destinationNodes = destinations.getNodes(network);
        Map<String, Double> destinationWeights = destinations.getWeights();


        // Get origin nodes
        log.info("Identifying origin nodes within boundary...");
        Set<Id<Node>> nodes = NetworkUtils2.getNodesInBoundary(network,regionBoundary);

        // Node accessibility calculation
        log.info("Running node accessibility calculation...");
        long startTime = System.currentTimeMillis();
        Map<Id<Node>,Double> nodeResults = NodeCalculator.calculate(network, nodes,
                destinationNodes, destinationWeights, tt, td, veh, df);
        long endTime = System.currentTimeMillis();
        log.info("Calculation time: " + (endTime - startTime));

        // Output nodes as CSV (if it was provided in properties file)
        if(outputNodesFileName != null) {
            AccessibilityWriter.writeNodesAsGpkg(nodeResults,fullNetwork,outputNodesFileName);
        }

        // Check on whether to perform GRID calculation
        if((inputGridFileName == null || outputGridFileName == null)) {
            log.warn("Input/output grid files not given. Skipping grid calculation.");
            return;
        }

        // Get grid
        GridData grid = new GridData(inputGridFileName);

        // Grid accessibility calculation
        log.info("Running grid accessibility calculation...");
        GridCalculator.calculate(network,nodeResults,grid.getGrid(),grid.getSideLength(),
                destinationNodes,destinationWeights,tt,td,veh,df);

        // Output grid as gpkg
        log.info("Saving results grid to " + outputGridFileName);
        GisUtils.writeFeaturesToGpkg(grid.getGrid(),grid.getDescription() + "_result",outputGridFileName);
    }
}
