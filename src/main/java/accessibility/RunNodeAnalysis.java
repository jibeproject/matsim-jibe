package accessibility;

import accessibility.impedance.DecayFunction;
import accessibility.impedance.DecayFunctions;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

// Currently considers walking and cycling accessibility only
public class RunNodeAnalysis {

    private final static Logger log = Logger.getLogger(RunNodeAnalysis.class);

    private final static GeometryFactory gf = new GeometryFactory();
    private static final Map<String, List<Coord>> destinationCoords = new LinkedHashMap<>();
    private static final Map<String, Double> destinationWeights = new LinkedHashMap<>();

    private static Integer numberOfThreads;
    private static Network fullNetwork;

    private static Geometry originBoundary;
    private static Geometry destinationBoundary;

    public static void main(String[] args) throws IOException, ParseException {
        if(args.length != 6) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Network file path \n" +
                    "(1) Origin boundary shapefile \n" +
                    "(2) Destination boundary shapefile \n" +
                    "(3) Destination coordinates File \n" +
                    "(4) Output folder path \n" +
                    "(5) Number of threads \n");
        }

        String networkPath = args[0];
        String originBoundaryFile = args[1];
        String destinationBoundaryFile = args[2];
        String destinationCoordsFile = args[3];
        String outputFolder = args[4];
        numberOfThreads = Integer.parseInt(args[5]);

        // Read network
        log.info("Reading MATSim network...");
        fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(networkPath);

        // Read boundary shapefiles
        log.info("Reading boundary shapefile...");
        originBoundary = GpkgReader.readBoundary(originBoundaryFile);
        destinationBoundary = GpkgReader.readBoundary(destinationBoundaryFile);

        // Check that origin boundary is within destination boundary
        if(!originBoundary.within(destinationBoundary)) {
            throw new RuntimeException("Origin boundary must be within destination boundary!");
        }

        // Load destination data
        loadDestinationData(destinationCoordsFile);

        // MODIFY THE FOLLOWING TO WORK WITH DIFFERENT ACCESSIBILITY TYPES
        IdMap<Node,Double> accessibilities = runAnalysis(AccessibilityData.WalkDistAccessibility(), DecayFunctions.WALK_DIST);
        AccessibilityWriter.writeNodesAsCsv(accessibilities, outputFolder + "/walkFoodDist001203989.csv");
        AccessibilityWriter.writeNodesAsGpkg(accessibilities,fullNetwork, outputFolder + "/walkFoodDist001203989.gpkg");
    }


    private static IdMap<Node,Double> runAnalysis(AccessibilityData data, DecayFunction decayFunction) {

        // Mode specific network
        Network network = NetworkUtils2.extractModeSpecificNetwork(fullNetwork, data.mode);

        // Origin nodes
        log.info("Processing origin nodes...");
        Set<Node> originNodes = new HashSet<>();
        Counter counter = new Counter("Processing origin node ", " / " + network.getNodes().size());
        for(Node node : network.getNodes().values()) {
            counter.incCounter();
            Coord c = node.getCoord();
            if(originBoundary.contains(gf.createPoint(new Coordinate(c.getX(),c.getY())))) {
                originNodes.add(node);
            }
        }
        log.info("Full network has " + network.getNodes().size() + " nodes.");
        log.info("Identified " + originNodes.size() + " nodes within boundary to be used for analysis.");

        // Map coords to nodes
        log.info("Processing destination nodes...");
        Map<String, List<Node>> destinationNodes = buildIdNodeMap(network, network);

        // Accessibility calculation
        log.info("Running accessibility calculation...");
        long startTime = System.currentTimeMillis();
        IdMap<Node,Double> accessibilities = NodeCalculator.calculate(
                network, originNodes, destinationWeights, destinationNodes,
                data.disutility, data.vehicle, decayFunction, numberOfThreads);
        long endTime = System.currentTimeMillis();
        log.info("Calculation time: " + (endTime - startTime));

        // Return
        return accessibilities;
    }

    private static void loadDestinationData(String filename) throws IOException {
        int lines = 0;
        int destinationsOutsideBoundary = 0;

        String expectedHeader = "ID,X,Y,WEIGHT";
        try (BufferedReader reader = IOUtils.getBufferedReader(filename)) {
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new RuntimeException("Bad header, expected '" + expectedHeader + "', got: '" + header + "'.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                lines++;
                String[] parts = StringUtils.explode(line, ',');
                String id = parts[0];
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);

                if(destinationBoundary.contains(gf.createPoint(new Coordinate(x,y)))) {
                    double wt = Double.parseDouble(parts[3]);
                    if(destinationCoords.containsKey(id)) {
                        if(destinationWeights.get(id) == wt) {
                            destinationCoords.get(id).add(new Coord(x,y));
                        } else {
                            throw new RuntimeException("Mismatching weights for destination " + id);
                        }
                    } else {
                        List<Coord> coords = new ArrayList<>();
                        coords.add(new Coord(x,y));
                        destinationCoords.put(id, coords);
                        destinationWeights.put(id, wt);
                    }
                } else {
                    destinationsOutsideBoundary++;
                }
            }
        }
        log.info("Read " + lines + " lines.");
        log.info("Loaded " + destinationCoords.size() + " unique destinations and " +
                destinationCoords.values().stream().mapToInt(List::size).sum() + " access points.");
        log.info(destinationsOutsideBoundary + " destinations ignored because their coordinates were outside the boundary.");
    }

    private static Map<String, List<Node>> buildIdNodeMap(Network xy2lNetwork, Network routingNetwork) {
        Map<String, List<Node>> idNodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<Coord>> e : destinationCoords.entrySet()) {
            List<Node> nodes = new ArrayList<>();
            for (Coord coord : e.getValue()) {
                nodes.add(routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, coord).getToNode().getId()));
            }
            idNodeMap.put(e.getKey(), nodes);
        }
        return idNodeMap;
    }
}