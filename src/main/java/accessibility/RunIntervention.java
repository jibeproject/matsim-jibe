package accessibility;

import accessibility.decay.*;
import accessibility.resources.AccessibilityProperties;
import accessibility.resources.AccessibilityResources;
import gis.GisUtils;
import gis.GpkgReader;
import io.ioUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import resources.Resources;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class RunIntervention {

    public static final Logger log = Logger.getLogger(RunIntervention.class);
    private final static char SEP = ',';
    private static Network fullNetwork;
    private static Geometry regionBoundary;
    private static Geometry networkBoundary;

    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            throw new RuntimeException("Program requires at least 2 arguments: \n" +
                    "(0) General Properties file\n" +
                    "(1) Intervention properties file(s) \n");
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
            runIntervention(args[i]);
        }
    }

    private static void runIntervention(String propertiesFilepath) throws IOException {

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
        String developmentAreasFile = AccessibilityResources.instance.getString(AccessibilityProperties.DEVELOPMENT_AREAS);
        String populationFile = AccessibilityResources.instance.getString(AccessibilityProperties.POPULATION);
        String currentDestinationsFile = AccessibilityResources.instance.getString(AccessibilityProperties.CURRENT_DESTINATIONS);
        String newDestinationsFile = AccessibilityResources.instance.getString(AccessibilityProperties.NEW_DESTINATIONS);
        String newAccessibilitiesFile = AccessibilityResources.instance.getString(AccessibilityProperties.NEW_ACCESSIBILITIES);
        double newDestinationWeight = AccessibilityResources.instance.getDouble(AccessibilityProperties.NEW_DESTINATION_WEIGHT);
        int newDestinationCount = AccessibilityResources.instance.getInt(AccessibilityProperties.NEW_DESTINATION_COUNT);

        // Read current destinations
        LocationData currentDestinations = new LocationData(currentDestinationsFile,networkBoundary);
        Map<Id<Node>,Double> currentDestinationsMap = currentDestinations.getNodeWeightMap(network);

        // Read population
        LocationData populationData = new LocationData(populationFile,regionBoundary);
        Map<String,Id<Node>> populationNodeMap = populationData.getIndividualNodes(network);
        IdMap<Node,Double> populationMap = new IdMap<>(Node.class);
        for(Map.Entry<String,Double> e : populationData.getWeights().entrySet()) {
            populationMap.put(populationNodeMap.get(e.getKey()),e.getValue());
        }
        Set<Id<Node>> populationNodes = populationMap.keySet();

        // Get destination weight
        if(Double.isNaN(newDestinationWeight)) {
            newDestinationWeight = currentDestinations.getWeights().values().stream().mapToDouble(v -> v).average().orElseThrow();
            log.warn("No destination weights specified in properties. Used mean weight of existing locations.");
        }
        log.info("New destinations will be assigned a weight of " + newDestinationWeight);

        // Areas that can be developed & candidate nodes
        Set<SimpleFeature> developmentAreas = new HashSet<>(GisUtils.readGpkg(developmentAreasFile));
        developmentAreas.removeIf(area -> ((Geometry) area.getDefaultGeometry()).isEmpty());

        // Get candidate nodes
        IdSet<Node> candidateNodes = GisUtils.getNodes(regionBoundary,developmentAreas,network);
        assert candidateNodes.size() > 0;

        // Decay function
        DecayFunction df = DecayFunctions.getFromProperties(network,networkBoundary);

        InterventionCalculator calc = new InterventionCalculator(network,tt,td,veh,df);

        List<Id<Node>> newNodes = new ArrayList<>();

        // Calcualte supply-side accessibility
        List<Map<Id<Node>,Double>> supply = new ArrayList<>(newDestinationCount);
        supply.add(0,calc.calculate(populationNodes,currentDestinationsMap));

        // Initialise
        Map<Id<Node>,Double> populationWeights = new HashMap<>(populationNodes.size());

        for(int i = 0 ; i < newDestinationCount ; i++) {

            // Update weights
            log.info("Updating weights...");
            Map<Id<Node>,Double> currSupply = supply.get(i);
            double minSupply = currSupply.values().stream().filter(v -> v > 0).min(Double::compare).orElseThrow() / 2;
            log.info("Min supply = " + minSupply);
            for(Id<Node> node : populationNodes) {
                double supplyValue = Math.max(currSupply.get(node),minSupply);
                populationWeights.put(node, populationMap.get(node) / supplyValue);
            }

            // Select candidate with highest demand
            log.info("Calculating demand for candidate nodes...");
            Map<Id<Node>,Double> demand = calc.calculate(candidateNodes,populationWeights);

            Id<Node> selected = null;
            double highest = Double.MIN_VALUE;
            for(Map.Entry<Id<Node>, Double> e : demand.entrySet()) {
                if(e.getValue() > highest) {
                    selected = e.getKey();
                    highest = e.getValue();
                }
            }
            assert selected != null;
            newNodes.add(selected);
            log.info("Destination " + i + " placed at node " + selected + ". Demand = " + highest);

            // Update supply-side accessibility results
            log.info("Updating supply...");
            Map<Id<Node>,Double> newSupply = new HashMap<>(populationNodes.size());
            Map<Id<Node>,Double> increase = calc.calculateSingle(populationNodes,selected,newDestinationWeight);
            for(Id<Node> node : populationNodes) {
                newSupply.put(node,currSupply.get(node) + increase.get(node));
            }
            supply.add(i+1,newSupply);
        }

        // Print new node locations
        log.info("Writing new node locations...");
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(newDestinationsFile),false);
        assert out != null;
        out.println("nodeId" + SEP + "x" + SEP + "y");
        for(Id<Node> nodeId : newNodes) {
            Node node = network.getNodes().get(nodeId);
            out.println(nodeId.toString() + SEP + node.getCoord().getX() + SEP + node.getCoord().getY());
        }
        out.close();

        // Print changes in population accessibility
        if(newAccessibilitiesFile != null) {
            log.info("Writing updates to population accessibility...");
            out = ioUtils.openFileForSequentialWriting(new File(newAccessibilitiesFile),false);
            assert out != null;

            // Write header
            StringBuilder builder = new StringBuilder();
            builder.append("id");
            builder.append(SEP);
            builder.append("node");
            for(int i = 0 ; i <= newDestinationCount ; i++) {
                builder.append(SEP);
                builder.append("it_");
                builder.append(i);
            }
            out.println(builder);

            // Write rows
            for(Map.Entry<String,Id<Node>> e : populationNodeMap.entrySet()) {
                builder = new StringBuilder();
                builder.append(e.getKey());
                builder.append(SEP);
                builder.append(e.getValue());
                for(int i = 0 ; i <= newDestinationCount ; i++) {
                    builder.append(SEP);
                    builder.append(supply.get(i).get(e.getValue()));
                }
                out.println(builder);
            }
            out.close();
        }
    }
}
