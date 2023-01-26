package accessibility.old;

import accessibility.impedance.DecayFunction;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.StringUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.opengis.feature.simple.SimpleFeature;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.WalkTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

// Currently considers cycling accessibility only
public class AccessibilityComparisonOld {

    private final static Logger log = Logger.getLogger(AccessibilityComparisonOld.class);
    private final static double MAX_BIKE_SPEED = 16 / 3.6;
    private final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final Map<String, List<Coord>> originCoords = new LinkedHashMap<>();
    private static final Map<String, List<Coord>> destinationCoords = new LinkedHashMap<>();
    private static final Map<String, Double> destinationWeights = new LinkedHashMap<>();

    private static String outputFolder;
    private static Integer numberOfThreads;
    private static Network fullNetwork;

    public static void main(String[] args) throws IOException, ParseException {
        if(args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Network File Path \n" +
                    "(1) Origin coordinates csv file OR Origin zones shapefile OR Origin boundary shapefile \n" +
                    "(2) Destination coordinates File \n" +
                    "(3) Output Path \n" +
                    "(4) Number of Threads \n");
        }

        String networkPath = args[0];
        String originCoordsFile = args[1];
        String destinationCoordsFile = args[2];
        outputFolder = args[3];
        numberOfThreads = Integer.parseInt(args[4]);

        // Read network
        log.info("Reading MATSim network...");
        fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(networkPath);

        // Read coords
        if (originCoordsFile.endsWith(".csv")) {
            loadSamplingPointsFromCsv(originCoordsFile);
        } else if (originCoordsFile.endsWith(".shp")) {
            loadSamplingPointsFromShp(6, originCoordsFile, "geo_code",new Random());
        }

        loadDestinationData(destinationCoordsFile);

        // Set up scenario and config
        log.info("Preparing Matsim config and scenario...");
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode(TransportMode.bike);
        config.addModule(bicycleConfigGroup);

        // Walk travel time
        TravelTime ttWalk = new WalkTravelTime();

        // Bike vehicle and travel time
        VehicleType type = VehicleUtils.createVehicleType(Id.create("routing", VehicleType.class));
        type.setMaximumVelocity(MAX_BIKE_SPEED);
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        Vehicle bike = VehicleUtils.createVehicle(Id.createVehicleId(1), type);
        TravelTime ttBike = new BicycleTravelTime(linkSpeedCalculator);

        // Set up disutility and impedance
        TravelDisutility tdBikeJibe = new JibeDisutility(TransportMode.bike,ttBike);
        TravelDisutility tdWalkJibe = new JibeDisutility(TransportMode.walk,ttWalk);

        // Analyses
        runAnalysis(TransportMode.bike, bike, c -> Math.exp(-0.04950999*c), ttBike, tdBikeJibe, "bikeJibe.csv");
        runAnalysis(TransportMode.bike, bike, c -> Math.exp(-0.0003104329*c), ttBike, new DistanceDisutility(), "bikeDist.csv");

        runAnalysis(TransportMode.walk, null, c -> Math.exp(-0.0974147*c), ttWalk, tdWalkJibe, "walkJibe.csv");
        runAnalysis(TransportMode.walk, null, c -> Math.exp(-0.001057006*c), ttWalk, new DistanceDisutility(), "walkDist.csv");

        // Analysis 800m

    }

    private static void loadSamplingPointsFromCsv(String filename) throws IOException {
        String expectedHeader = "ZONE;X;Y";
        try (BufferedReader reader = IOUtils.getBufferedReader(filename)) {
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new RuntimeException("Bad header, expected '" + expectedHeader + "', got: '" + header + "'.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = StringUtils.explode(line, ';');
                String zoneId = parts[0];
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                if(originCoords.containsKey(zoneId)) {
                    originCoords.get(zoneId).add(new Coord(x,y));
                } else {
                    List<Coord> coords = new ArrayList<>();
                    coords.add(new Coord(x,y));
                    originCoords.put(zoneId, coords);
                }
            }
            log.info("Loaded " + originCoords.size() + " origin zones with " +
                    originCoords.values().stream().mapToInt(List::size).sum() + " sampling points.");
        }
    }

    private static void runAnalysis(String mode, Vehicle veh, DecayFunction decayFunction, TravelTime tt, TravelDisutility td, String outputFileName) throws IOException {

        // Mode specific network
        Network network = NetworkUtils2.extractModeSpecificNetwork(fullNetwork, mode);
/*

        // Map coords to nodes
        Map<String, List<Node>> originNodes = buildIdNodeMap(originCoords, network, network);
        Map<String, List<Node>> destinationNodes = buildIdNodeMap(destinationCoords, network, network);


        long startTime = System.currentTimeMillis();
        AccessibilityData<String> accessibilities = AccessibilityCalculator.calculate(
                network, originCoords.keySet(), destinationWeights, originNodes, destinationNodes,
                tt, td, null, veh, impedance, numberOfThreads);
        long endTime = System.currentTimeMillis();
        log.info("Calculation time: " + (endTime - startTime));

        startTime = System.currentTimeMillis();
        AccessibilityWriter.writeAsCsv(accessibilities,outputFolder + "/" + outputFileName);
        endTime = System.currentTimeMillis();
        log.info("Writing time: " + (endTime - startTime));
        log.info("Finished processing " + outputFileName);
*/

    }

    private static void loadDestinationData(String filename) throws IOException, ParseException {
        String expectedHeader = "ID;X;Y;WEIGHT";
        NumberFormat format = NumberFormat.getInstance(Locale.GERMANY);
        try (BufferedReader reader = IOUtils.getBufferedReader(filename)) {
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new RuntimeException("Bad header, expected '" + expectedHeader + "', got: '" + header + "'.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = StringUtils.explode(line, ';');
                String id = parts[0];
                double x = format.parse(parts[1]).doubleValue();
                double y = format.parse(parts[2]).doubleValue();
                double wt = format.parse(parts[3]).doubleValue();

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
            }
        }
        log.info("Loaded " + destinationCoords.size() + " unique destinations and " +
                destinationCoords.values().stream().mapToInt(List::size).sum() + " access points.");
    }


    private static Map<String, List<Node>> buildIdNodeMap(Map<String, List<Coord>> idCoordMap, Network xy2lNetwork, Network routingNetwork) {
        Map<String, List<Node>> idNodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<Coord>> e : idCoordMap.entrySet()) {
            List<Node> nodes = new ArrayList<>();
            for (Coord coord : e.getValue()) {
                nodes.add(routingNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2lNetwork, coord).getToNode().getId()));
            }
            idNodeMap.put(e.getKey(), nodes);
        }
        return idNodeMap;
    }

    public static void loadSamplingPointsFromShp(int numberOfPointsPerZone, String zonesShapeFilename, String zonesIdAttributeName, Random r) {

        log.info("reading node coords from network");
        List<Coord> nodeCoords = new ArrayList<>(fullNetwork.getNodes().size());
        for (Node node : fullNetwork.getNodes().values()) {
            nodeCoords.add(node.getCoord());
        }

        log.info("loading zones from " + zonesShapeFilename);
        Collection<SimpleFeature> zones = new ShapeFileReader().readFileAndInitialize(zonesShapeFilename);
        SpatialIndex zonesQt = new Quadtree();
        for (SimpleFeature zone : zones) {
            Envelope envelope = ((Geometry) (zone.getDefaultGeometry())).getEnvelopeInternal();
            zonesQt.insert(envelope, zone);
        }

        log.info("assign locations to zones...");
        Map<String, List<Coord>> allCoordsPerZone = new HashMap<>();
        Counter counter = new Counter("# ");
        for (Coord coord : nodeCoords) {
            counter.incCounter();
            String zoneId = findZone(coord, zonesQt, zonesIdAttributeName);
            if (zoneId != null) {
                allCoordsPerZone.computeIfAbsent(zoneId, k -> new ArrayList<>()).add(coord);
            }
        }
        counter.printCounter();

        // define points per zone
        log.info("choose locations (sampling points) per zone...");
        for (Map.Entry<String, List<Coord>> e : allCoordsPerZone.entrySet()) {
            String zoneId = e.getKey();
            List<Coord> zoneFacilities = e.getValue();
            List<Coord> coords = new ArrayList<>(numberOfPointsPerZone);
            for (int i = 0; i < numberOfPointsPerZone; i++) {
                double weight = r.nextDouble() * zoneFacilities.size();
                double sum = 0.0;
                Coord chosenLoc = null;
                for (Coord loc : zoneFacilities) {
                    sum += 1.;
                    if (weight <= sum) {
                        chosenLoc = loc;
                        break;
                    }
                }
                coords.add(chosenLoc);
            }
            originCoords.put(zoneId, coords);
        }
    }

    private static String findZone(Coord coord, SpatialIndex zonesQt, String zonesIdAttributeName) {
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(coord.getX(), coord.getY()));
        List elements = zonesQt.query(pt.getEnvelopeInternal());
        for (Object o : elements) {
            SimpleFeature z = (SimpleFeature) o;
            if (((Geometry) z.getDefaultGeometry()).intersects(pt)) {
                return z.getAttribute(zonesIdAttributeName).toString();
            }
        }
        return null;
    }
}
