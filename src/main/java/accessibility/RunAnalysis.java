package accessibility;

import accessibility.decay.*;
import accessibility.resources.AccessibilityProperties;
import accessibility.resources.AccessibilityResources;
import gis.GisUtils;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.geotools.geometry.jts.Geometries;
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

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class RunAnalysis {

    public static final Logger log = Logger.getLogger(RunAnalysis.class);
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

    private static void runAnalysis(String propertiesFilepath) throws IOException {

        // Initialise properties file
        AccessibilityResources.initializeResources(propertiesFilepath);

        // Mode
        String mode = AccessibilityResources.instance.getMode();

        // Create mode-specific network
        Network network = NetworkUtils2.extractModeSpecificNetwork(fullNetwork,mode);

        // Travel time, vehicle, disutility
        TravelTime tt = AccessibilityResources.instance.getTravelTime();
        Vehicle veh = AccessibilityResources.instance.getVehicle();
        TravelDisutility td = AccessibilityResources.instance.getTravelDisutility();

        // Inputs/outputs
        String endLocationsFilename = AccessibilityResources.instance.getString(AccessibilityProperties.END_LOCATIONS);
        String outputNodesFilename = AccessibilityResources.instance.getString(AccessibilityProperties.OUTPUT_NODES);
        String inputFilename = AccessibilityResources.instance.getString(AccessibilityProperties.INPUT);
        String outputFilename = AccessibilityResources.instance.getString(AccessibilityProperties.OUTPUT);

        // Input locations (to calculate accessibility for)
        FeatureData features = new FeatureData(inputFilename);

        // Parameters
        DecayFunction df = DecayFunctions.getFromProperties(network,networkBoundary);
        boolean fwd = AccessibilityResources.instance.fwdCalculation();

        // Checks on whether to perform ANY calculations
        if(df == null) {
            log.warn("No decay function. Skipping all accessibility calculations.");
            return;
        }
        if(endLocationsFilename == null) {
            log.warn("No end locations given. Skipping all accessibility calculations.");
            return;
        }
        if (outputNodesFilename == null && (inputFilename == null || outputFilename == null)) {
            log.warn("No input/output files given. Skipping all accessibility calculations.");
            return;
        }
        LocationData endData = new LocationData(endLocationsFilename,networkBoundary);
        endData.estimateNetworkNodes(network);
        Map<String, IdSet<Node>> endNodes = endData.getNodes();
        Map<String, Double> endWeights = endData.getWeights();

        // Accessibility calculation on NODES (if using polygons or node output requested)
        Map<Id<Node>,Double> nodeResults = null;
        if(Geometries.POLYGON.equals(features.getGeometryType())
                || Geometries.MULTIPOLYGON.equals(features.getGeometryType())
                || outputNodesFilename != null) {

            // Get applicable start nodes
            log.info("Identifying origin nodes within area of analysis...");
            Set<Id<Node>> startNodes = NetworkUtils2.getNodesInBoundary(network,regionBoundary);

            // Run node accessibility calculation
            log.info("Running node accessibility calculation...");
            long startTime = System.currentTimeMillis();
            nodeResults = NodeCalculator.calculate(network, startNodes, endNodes, endWeights, fwd, tt, td, veh, df);
            long endTime = System.currentTimeMillis();
            log.info("Calculation time: " + (endTime - startTime));

            // Output nodes as CSV (if it was provided in properties file)
            if(outputNodesFilename != null) {
                AccessibilityWriter.writeNodesAsGpkg(nodeResults,fullNetwork,outputNodesFilename);
            }
        }

        if(inputFilename != null && outputFilename != null) {

            log.info("Running accessibility calculation...");
            FeatureCalculator.calculate(network, features.getCollection(), endNodes, endWeights,
                    nodeResults, features.getRadius(), fwd, tt, td, veh, df);

            // Output grid as gpkg
            log.info("Saving output features to " + outputFilename);
            GisUtils.writeFeaturesToGpkg(features.getCollection(), features.getDescription() + "_result", outputFilename);
        }
    }
}
