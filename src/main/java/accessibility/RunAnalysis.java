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
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import resources.Resources;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
        String inputFilename = AccessibilityResources.instance.getString(AccessibilityProperties.INPUT);

        List<String> endLocationsFilenames = AccessibilityResources.instance.getStringList(AccessibilityProperties.END_LOCATIONS);
        List<String> endLocationsDescriptions = AccessibilityResources.instance.getStringList(AccessibilityProperties.END_DESCRIPTION);
        List<Double> endLocationsAlpha = AccessibilityResources.instance.getStringList(AccessibilityProperties.END_ALPHA).stream().map(Double::parseDouble).collect(Collectors.toList());

        String outputNodesFilename = AccessibilityResources.instance.getString(AccessibilityProperties.OUTPUT_NODES);
        String outputFeaturesFilename = AccessibilityResources.instance.getString(AccessibilityProperties.OUTPUT_FEATURES);

        // Input locations (to calculate accessibility for)
        FeatureData features = new FeatureData(inputFilename, endLocationsDescriptions);

        // Parameters
        DecayFunction df = DecayFunctions.getFromProperties(network,networkBoundary);
        Boolean fwd = AccessibilityResources.instance.fwdCalculation();

        // Checks on whether to perform ANY calculations
        if(df == null) {
            log.error("No decay function. Skipping all accessibility calculations.");
            return;
        }
        if(endLocationsFilenames.size() == 0) {
            log.error("No end locations given. Skipping all accessibility calculations.");
            return;
        }
        int endLocationsSize = endLocationsFilenames.size();
        if(endLocationsSize != endLocationsDescriptions.size()) {
            log.error("Number of end locations does not match number of end descriptions.");
        }
        if (outputNodesFilename == null && (inputFilename == null || outputFeaturesFilename == null)) {
            log.error("No input/output files given. Skipping all accessibility calculations.");
            return;
        }

        List<LocationData> endDataList = new ArrayList<>(endLocationsSize);
        for(int i = 0 ; i < endLocationsSize ; i++) {
            LocationData endData = new LocationData(endLocationsDescriptions.get(i),endLocationsFilenames.get(i),networkBoundary);
            endData.estimateNetworkNodes(network);
            endData.transformWeights(endLocationsAlpha.get(i));
            endDataList.add(endData);
        }

        // Accessibility calculation on NODES (if using polygons)
        Map<Id<Node>,double[]> nodeResults = null;
        if(Geometries.POLYGON.equals(features.getGeometryType())
                || Geometries.MULTIPOLYGON.equals(features.getGeometryType())) {

            // Get applicable start nodes
            log.info("Identifying origin nodes within area of analysis...");
            Set<Id<Node>> startNodes = NetworkUtils2.getNodesInBoundary(network,regionBoundary);

            // Run node accessibility calculation
            log.info("Running node accessibility calculation...");
            long startTime = System.currentTimeMillis();
            nodeResults = NodeCalculator.calculate(network, startNodes, endDataList, fwd, tt, td, veh, df);
            long endTime = System.currentTimeMillis();
            log.info("Calculation time: " + (endTime - startTime));

            // Output nodes as CSV (if it was provided in properties file)
            if(outputNodesFilename != null) {
                AccessibilityWriter.writeNodesAsGpkg(nodeResults,endLocationsDescriptions,fullNetwork,outputNodesFilename);
            }
        }

        if(inputFilename != null && outputFeaturesFilename != null) {

            log.info("Running accessibility calculation...");
            FeatureCalculator.calculate(network, features.getCollection(), endDataList,
                    nodeResults, features.getRadius(), fwd, tt, td, veh, df);

            // Output grid as gpkg
            log.info("Saving output features to " + outputFeaturesFilename);
            GisUtils.writeFeaturesToGpkg(features.getCollection(), features.getDescription() + "_result", outputFeaturesFilename);
        }
    }
}