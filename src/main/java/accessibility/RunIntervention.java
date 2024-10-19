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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        if(df == null) {
            log.error("No decay function. Skipping all accessibility calculations.");
            return;
        }

        // Existing destinations
        List<String> destinationFilenames = AccessibilityResources.instance.getStringList(AccessibilityProperties.END_LOCATIONS);
        List<String> destinationDescriptions = AccessibilityResources.instance.getStringList(AccessibilityProperties.END_DESCRIPTION);
        List<Double> destinationAlphas = AccessibilityResources.instance.getStringList(AccessibilityProperties.END_ALPHA).stream().map(Double::parseDouble).collect(Collectors.toList());

        // Checks
        int destTypeCount = destinationFilenames.size();
        if(destinationFilenames.size() == 0) {
            log.error("No end locations given. Skipping all accessibility calculations.");
            return;
        }
        if(destTypeCount != destinationDescriptions.size()) {
            log.error("Number of end locations does not match number of end descriptions.");
            return;
        }
        if(destTypeCount != destinationAlphas.size()) {
            log.error("Number of end locations does not match number of alpha parameters.");
            return;
        }

        // Other input files
        String developmentAreasFile = AccessibilityResources.instance.getString(AccessibilityProperties.DEVELOPMENT_AREAS);
        String populationFile = AccessibilityResources.instance.getString(AccessibilityProperties.POPULATION);

        // Output files
        String destinationsOutputFile = AccessibilityResources.instance.getString(AccessibilityProperties.DESTINATION_OUTPUT);
        String supplyOutputFile = AccessibilityResources.instance.getString(AccessibilityProperties.SUPPLY_OUTPUT);
        String demandOutputFile = AccessibilityResources.instance.getString(AccessibilityProperties.DEMAND_OUTPUT);

        // Termination criteria todo: add other termination criteria (should be specified in properties)
        int maxDestinations = AccessibilityResources.instance.getInt(AccessibilityProperties.MAX_DESTINATIONS);
        assert maxDestinations > 0;

        // READ INPUTS

        // Read population
        LocationData populationData = new LocationData(null,populationFile,regionBoundary);
        populationData.estimateNetworkNodes(network);
        IdMap<Node,String> populationNodeIdMap = populationData.getNodeIdMap();
        IdMap<Node,Double> population = populationData.getNodeWeightMap();
        Set<Id<Node>> populationNodes = population.keySet();

        // Read destinations
        List<LocationData> destinationDataList = new ArrayList<>(destTypeCount);
        double[] newDestinationWeight = new double[destTypeCount];
        for(int i = 0 ; i < destTypeCount ; i++) {
            LocationData endData = new LocationData(destinationDescriptions.get(i),destinationFilenames.get(i),networkBoundary);
            endData.estimateNetworkNodes(network);
            endData.transformWeights(destinationAlphas.get(i));
            destinationDataList.add(endData);
            Map<String,Double> weights = endData.getWeights();
            int destCount = weights.size();
            newDestinationWeight[i] = weights.values().stream().sorted(Double::compare).collect(Collectors.toList()).get(destCount / 2);
            log.info("DESTINATION TYPE: " + endData.getDescription().toUpperCase() + " COUNT: " + destCount + " MEDIAN WEIGHT: " + newDestinationWeight[i]);
        }



        // Get candidate nodes for new destinations
        log.info("Reading candidate development areas...");
        Set<SimpleFeature> developmentAreas = new HashSet<>(GisUtils.readGpkg(developmentAreasFile));
        developmentAreas.removeIf(area -> ((Geometry) area.getDefaultGeometry()).isEmpty());
        IdMap<Node,String> candidateNodeIdMap = GisUtils.getCandidateNodes(regionBoundary,developmentAreas,network);
        assert candidateNodeIdMap.size() > 0;

        // Supply and demand results
        List<Map<Id<Node>,double[]>> supply = new ArrayList<>();
        List<Map<Id<Node>,double[]>> demand = new ArrayList<>();
        List<List<Id<Node>>> newDestinations = new ArrayList<>();


        // Initialise calculator
        InterventionCalculator calc = new InterventionCalculator(network,tt,td,veh,df);

        // Calculate supply-side accessibility
        supply.add(0,calc.calculate(populationNodes,destinationDataList,null));

        // Main loop
        int i = 0;
        do {

            // Get current supply
            Map<Id<Node>, double[]> currSupply = supply.get(i);

            // Compute demand for all candidate nodes
            log.info("Calculating demand for candidate nodes...");
            demand.add(i, calc.calculateDemand(candidateNodeIdMap.keySet(), population, currSupply, newDestinationWeight));

            // Select candidate with the highest demand
            List<Id<Node>> currDestinations = new ArrayList<>();
            for (int j = 0; j < destTypeCount; j++) {
                Id<Node> selected = null;
                double highest = Double.MIN_VALUE;
                for (Map.Entry<Id<Node>, double[]> e : demand.get(i).entrySet()) {
                    double value = e.getValue()[j];
                    if (value > highest) {
                        selected = e.getKey();
                        highest = value;
                    }
                }
                assert selected != null;
                currDestinations.add(j,selected);
                log.info(destinationDescriptions.get(j) + " No " + i + " placed at node " + selected + ". Demand = " + highest);
            }
            newDestinations.add(currDestinations);

            // Increment iteration number
            i++;

            // Update population accessibility
            log.info("Updating supply...");
            Map<Id<Node>, double[]> increase = calc.calculateReverse(currDestinations, newDestinationWeight, populationNodes);
            Map<Id<Node>, double[]> newSupply = new HashMap<>(populationNodes.size());
            populationNodes.forEach(nodeId -> newSupply.put(nodeId,IntStream.range(0, destTypeCount).mapToDouble(j -> (currSupply.get(nodeId))[j] + (increase.get(nodeId))[j]).toArray()));

            // Store current supply
            supply.add(i, newSupply);

            // Termination criteria
        } while (i < maxDestinations);

        // Write new node locations
        log.info("Writing new node locations...");
        printNewDestinations(destinationsOutputFile, network, candidateNodeIdMap, demand, newDestinations, newDestinationWeight, destinationDescriptions);

        // Write changes in population accessibility
        if(demandOutputFile != null) {
            log.info("Writing demand-side output for each (potential) destination node...");
            writeEachIteration(demandOutputFile, network, candidateNodeIdMap, demand, destinationDescriptions);
        }

        if(supplyOutputFile != null) {
            log.info("Writing supply-side output for each population location and iteration...");
            writeEachIteration(supplyOutputFile, network, populationNodeIdMap, supply, destinationDescriptions);
        }
    }


    // Prints new destinations and details
    private static void printNewDestinations(String outputFile, Network network, IdMap<Node,String> nodes,
                                             List<Map<Id<Node>,double[]>> demand, List<List<Id<Node>>> newNodes,
                                             double[] newWeights,
                                             List<String> destinationDescriptions) {
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputFile),false);
        assert out != null;

        // Write header
        out.println("type" + SEP + "n" + SEP + "id" + SEP + "nodeId" + SEP + "x" + SEP + "y" + SEP + "weight" + SEP + "demand");

        // Write rows
        int i = 0;
        for(List<Id<Node>> iteration : newNodes) {
            int j = 0;
            for(Id<Node> nodeId : iteration) {
                Coord coord = network.getNodes().get(nodeId).getCoord();
                String line = destinationDescriptions.get(j) + SEP + i + SEP + nodes.get(nodeId) + SEP + nodeId.toString() + SEP +
                        coord.getX() + SEP + coord.getY() + SEP + newWeights[j] + SEP + demand.get(i).get(nodeId)[j];
                out.println(line);
                j++;
            }
            i++;
        }
        out.close();
    }

    private static void writeEachIteration(String outputFile, Network network, IdMap<Node,String> nodes, List<Map<Id<Node>,double[]>> results,
                                           List<String> destinationDescriptions) {
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputFile),false);
        assert out != null;

        int iterations = results.size();

        // Write header
        StringBuilder builder = new StringBuilder();
        builder.append("id").append(SEP).append("node").append(SEP).append("x").append(SEP).append("y").append(SEP).append("type");
        for(int i = 0 ; i < iterations ; i++) {
            builder.append(SEP).append("it_").append(i);
        }
        out.println(builder);

        // Write rows
        for(Map.Entry<Id<Node>,String> e : nodes.entrySet()) {
            Coord coord = network.getNodes().get(e.getKey()).getCoord();
            for (int j = 0; j < destinationDescriptions.size(); j++) {
                builder = new StringBuilder();
                builder.append(e.getValue()).append(SEP).append(e.getKey()).append(SEP).append(coord.getX()).append(SEP).append(coord.getY()).append(SEP).append(destinationDescriptions.get(j));
                for (int i = 0; i < iterations; i++) {
                    builder.append(SEP).append(results.get(i).get(e.getKey())[j]);
                }
                out.println(builder);
            }
        }
        out.close();
    }
}
