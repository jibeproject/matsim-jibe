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
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
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

        // Travel time, vehicle, disutility, decay
        TravelTime tt = AccessibilityResources.instance.getTravelTime();
        Vehicle veh = AccessibilityResources.instance.getVehicle();
        TravelDisutility td = AccessibilityResources.instance.getTravelDisutility();
        DecayFunction df = DecayFunctions.getFromProperties(network,networkBoundary);

        // Input files
        String developmentAreasFile = AccessibilityResources.instance.getString(AccessibilityProperties.DEVELOPMENT_AREAS);
        String populationFile = AccessibilityResources.instance.getString(AccessibilityProperties.POPULATION);
        String currentDestinationsFile = AccessibilityResources.instance.getString(AccessibilityProperties.CURRENT_DESTINATIONS);

        // Output files
        String destinationsOutputFile = AccessibilityResources.instance.getString(AccessibilityProperties.DESTINATION_OUTPUT);
        String supplyOutputFile = AccessibilityResources.instance.getString(AccessibilityProperties.SUPPLY_OUTPUT);
        String demandOutputFile = AccessibilityResources.instance.getString(AccessibilityProperties.DEMAND_OUTPUT);

        // Termination criteria todo: add other termination criteria (should be specified in properties)
        int maxDestinations = AccessibilityResources.instance.getInt(AccessibilityProperties.MAX_DESTINATIONS);
        assert maxDestinations > 0;

        // Read population
        LocationData populationData = new LocationData(populationFile,regionBoundary);
        populationData.estimateNetworkNodes(network);
        IdMap<Node,String> populationNodeIdMap = populationData.getNodeIdMap();
        IdMap<Node,Double> populationNodeWtMap = populationData.getNodeWeightMap();
        Set<Id<Node>> populationNodes = populationNodeWtMap.keySet();

        // Read current destinations
        LocationData currentDestinations = new LocationData(currentDestinationsFile,networkBoundary);
        currentDestinations.estimateNetworkNodes(network);
        IdMap<Node,Double> destinationNodeWtMap = currentDestinations.getNodeWeightMap();

        // Get candidate nodes for new destinations
        Set<SimpleFeature> developmentAreas = new HashSet<>(GisUtils.readGpkg(developmentAreasFile));
        developmentAreas.removeIf(area -> ((Geometry) area.getDefaultGeometry()).isEmpty());
        IdMap<Node,String> candidateNodeIdMap = GisUtils.getCandidateNodes(regionBoundary,developmentAreas,network);
        assert candidateNodeIdMap.size() > 0;

        // Dataset of new destinations
        Map<Id<Node>,Double> newDestinations = new LinkedHashMap<>();

        // New destination weight todo: make weight vary depending on available space in brownfield area
        double newDestinationWeight = AccessibilityResources.instance.getDouble(AccessibilityProperties.NEW_DESTINATION_WEIGHT);
        if(Double.isNaN(newDestinationWeight)) {
            log.warn("No destination weights specified in properties. Using mean weight of existing locations.");
            newDestinationWeight = currentDestinations.getWeights().values().stream().mapToDouble(v -> v).average().orElseThrow();
        }
        log.info("New destinations will be assigned a weight of " + newDestinationWeight);

        // Supply and demand results
        List<Map<Id<Node>,Double>> supply = new ArrayList<>();
        List<Map<Id<Node>,Double>> demand = new ArrayList<>();

        // Initialise calculator
        InterventionCalculator calc = new InterventionCalculator(network,tt,td,veh,df);

        // Calculate supply-side accessibility
        supply.add(0,calc.calculate(populationNodes,destinationNodeWtMap));

        // Initialise
        Map<Id<Node>,Double> populationWeights = new HashMap<>(populationNodes.size());

        int i = 0;

        while(true) {

            // Update weights
            log.info("Updating weights...");
            Map<Id<Node>,Double> currSupply = supply.get(i);
            double minSupply = currSupply.values().stream().filter(v -> v > 0).min(Double::compare).orElseThrow() / 2;
            log.info("Min supply = " + minSupply);
            for(Id<Node> node : populationNodes) {
                double supplyValue = Math.max(currSupply.get(node), minSupply);
                populationWeights.put(node, populationNodeWtMap.get(node) / supplyValue);
            }

            // Select candidate with the highest demand
            log.info("Calculating demand for candidate nodes...");
            demand.add(i,calc.calculate(candidateNodeIdMap.keySet(),populationWeights));

            Id<Node> selected = null;
            double highest = Double.MIN_VALUE;
            for(Map.Entry<Id<Node>, Double> e : demand.get(i).entrySet()) {
                if(e.getValue() > highest) {
                    selected = e.getKey();
                    highest = e.getValue();
                }
            }
            assert selected != null;
            newDestinations.put(selected,newDestinationWeight);
            log.info("Destination " + i + " placed at node " + selected + ". Demand = " + highest);

            // Increment iteration number
            i++;

            // Update supply-side accessibility results
            log.info("Updating supply...");
            Map<Id<Node>,Double> newSupply = new HashMap<>(populationNodes.size());
            Map<Id<Node>,Double> increase = calc.calculateSingle(populationNodes,selected,newDestinationWeight);
            for(Id<Node> node : populationNodes) {
                newSupply.put(node,currSupply.get(node) + increase.get(node));
            }

            // Store current supply
            supply.add(i,newSupply);

            // Termination criteria
            if (i >= maxDestinations) {
                break;
            }
        }

        // Write new node locations
        log.info("Writing new node locations...");
        printNewDestinations(destinationsOutputFile, network, candidateNodeIdMap, demand, newDestinations);

        // Write changes in population accessibility
        if(demandOutputFile != null) {
            log.info("Writing demand-side output for each (potential) destination node...");
            writeEachIteration(demandOutputFile, network, candidateNodeIdMap, demand);
        }

        if(supplyOutputFile != null) {
            log.info("Writing supply-side output for each population location and iteration...");
            writeEachIteration(supplyOutputFile, network, populationNodeIdMap, supply);
        }
    }

    private static void printNewDestinations(String outputFile, Network network, IdMap<Node,String> nodes, List<Map<Id<Node>,Double>> demand, Map<Id<Node>,Double> results) {
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputFile),false);
        assert out != null;

        // Write header
        out.println("n" + SEP + "id" + SEP + "nodeId" + SEP + "x" + SEP + "y" + SEP + "weight" + SEP + "demand");

        // Write rows
        int i = 0;
        for(Id<Node> nodeId : results.keySet()) {
            Coord coord = network.getNodes().get(nodeId).getCoord();
            String line = Integer.toString(i) + SEP + nodes.get(nodeId) + SEP + nodeId.toString() + SEP +
                    coord.getX() + SEP + coord.getY() + SEP + results.get(nodeId) + SEP + demand.get(i).get(nodeId);
            out.println(line);
            i++;
        }
        out.close();
    }

    private static void writeEachIteration(String outputFile, Network network, IdMap<Node,String> nodes, List<Map<Id<Node>,Double>> results) {
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputFile),false);
        assert out != null;

        int iterations = results.size();

        // Write header
        StringBuilder builder = new StringBuilder();
        builder.append("id").append(SEP).append("node").append(SEP).append("x").append(SEP).append("y");
        for(int i = 0 ; i < iterations ; i++) {
            builder.append(SEP).append("it_").append(i);
        }
        out.println(builder);

        // Write rows
        for(Map.Entry<Id<Node>,String> e : nodes.entrySet()) {
            builder = new StringBuilder();
            Coord coord = network.getNodes().get(e.getKey()).getCoord();
            builder.append(e.getValue()).append(SEP).append(e.getKey()).append(SEP).append(coord.getX()).append(SEP).append(coord.getY());
            for(int i = 0 ; i < iterations ; i++) {
                builder.append(SEP).append(results.get(i).get(e.getKey()));
            }
            out.println(builder);
        }
        out.close();
    }
}
