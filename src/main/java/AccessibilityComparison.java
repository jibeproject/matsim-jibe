import ch.sbb.matsim.analysis.Impedance;
import ch.sbb.matsim.analysis.calc.AccessibilityCalculator;
import ch.sbb.matsim.analysis.data.AccessibilityData;
import ch.sbb.matsim.analysis.io.AccessibilityWriter;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
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
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.StringUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import routing.disutility.JibeDisutility;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.WalkTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Currently considers cycling accessibility only
public class AccessibilityComparison {

    private final static Logger log = Logger.getLogger(RouteComparison.class);
    private final static double MAX_BIKE_SPEED = 16 / 3.6;
    private final static String MODE = TransportMode.bike;

    public static void main(String[] args) throws IOException, ParseException {
        if(args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Network File Path \n" +
                    "(1) Origin coordinates File \n" +
                    "(2) Destination coordinates File \n" +
                    "(3) Output File Path \n" +
                    "(4) Number of Threads \n");
        }

        String networkPath = args[0];
        String originCoordsFile = args[1];
        String destinationCoordsFile = args[2];
        String outputFileName = args[3];
        int numberOfThreads = Integer.parseInt(args[4]);

        // Read network
        log.info("Reading MATSim network...");
        Network fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(networkPath);
        Network network = NetworkUtils2.extractModeSpecificNetwork(fullNetwork, MODE);

        // Set up scenario and config
        log.info("Preparing Matsim config and scenario...");
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode(TransportMode.bike);
        config.addModule(bicycleConfigGroup);

        // Read coords
        Map<String, Coord> originCoords = loadOriginData(originCoordsFile);
        Map<String, Coord> destinationCoords = loadDestinationData(destinationCoordsFile);

        // Map coords to nodes
        Map<String, Node> originNodes = buildZoneNodeMap(originCoords, network, network);
        Map<String, Node> destinationNodes = buildZoneNodeMap(destinationCoords, network, network);

        // CREATE VEHICLE & SET UP TRAVEL TIME
        Vehicle veh;
        TravelTime tt;
        Impedance impedance;
        if(MODE.equals(TransportMode.walk)) {
            veh = null;
            tt = new WalkTravelTime();
            impedance = c -> Math.exp(-0.0974 * c);
        } else if (MODE.equals(TransportMode.bike)) {
            VehicleType type = VehicleUtils.createVehicleType(Id.create("routing", VehicleType.class));
            type.setMaximumVelocity(MAX_BIKE_SPEED);
            BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
            veh = VehicleUtils.createVehicle(Id.createVehicleId(1), type);
            tt = new BicycleTravelTime(linkSpeedCalculator);
            impedance = c -> Math.exp(-0.0495 * c);
        } else {
            throw new RuntimeException("Routing not set up for mode " + MODE);
        }

        // Set up impedance
        TravelDisutility td = new JibeDisutility(MODE,tt);

        long startTime = System.currentTimeMillis();
        AccessibilityData<String> accessibilities = AccessibilityCalculator.calculate(
                network, originCoords.keySet(), destinationCoords, originNodes, destinationNodes,
                tt, td, null, veh, impedance, numberOfThreads);
        long endTime = System.currentTimeMillis();
        log.info("Calculation time: " + (endTime - startTime));

        startTime = System.currentTimeMillis();
        AccessibilityWriter.writeAsCsv(accessibilities,outputFileName);
        endTime = System.currentTimeMillis();
        log.info("Writing time: " + (endTime - startTime));
    }

    private static Map<String, Coord> loadOriginData(String filename) throws IOException {
        String expectedHeader = "ZONE;X;Y";
        Map<String, Coord> zoneCoordMap = new LinkedHashMap<>();
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
                Coord coord = new Coord(x, y);
                zoneCoordMap.put(zoneId,coord);
            }
        }
        return zoneCoordMap;
    }

    private static Map<String, Coord> loadDestinationData(String filename) throws IOException, ParseException {
        String expectedHeader = "ID;X;Y;WEIGHT";
        Map<String, Coord> zoneCoordMap = new LinkedHashMap<>();
        NumberFormat format = NumberFormat.getInstance(Locale.GERMANY);
        try (BufferedReader reader = IOUtils.getBufferedReader(filename)) {
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new RuntimeException("Bad header, expected '" + expectedHeader + "', got: '" + header + "'.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = StringUtils.explode(line, ',');
                String zoneId = parts[0];
                double x = format.parse(parts[1]).doubleValue();
                double y = format.parse(parts[2]).doubleValue();
                double wt = format.parse(parts[3]).doubleValue();
                Coord coord = new Coord(x, y, wt);
                zoneCoordMap.put(zoneId,coord);
            }
        }
        return zoneCoordMap;
    }

    private static <T> Map<T, Node> buildZoneNodeMap(Map<T, Coord> zoneCoordMap, Network xy2lNetwork, Network routingNetwork) {
        Map<T, Node> zoneNodeMap = new LinkedHashMap<>();
        for (Map.Entry<T, Coord> e : zoneCoordMap.entrySet()) {
            T zoneId = e.getKey();
            Coord coord = e.getValue();
            if(coord.hasZ()) {
                coord = new Coord(coord.getX(), coord.getY());
            }
            Node node = routingNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2lNetwork, coord).getToNode().getId());
            zoneNodeMap.put(zoneId, node);
        }
        return zoneNodeMap;
    }

}
