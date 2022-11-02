import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.StringUtils;
import routing.travelTime.WalkTravelTime;
import routing.disutility.components.LinkAttractiveness;
import routing.disutility.components.JctStress;
import ch.sbb.matsim.analysis.calc.IndicatorCalculator;
import ch.sbb.matsim.analysis.data.IndicatorData;
import ch.sbb.matsim.analysis.io.IndicatorWriter;
import routing.disutility.JibeWalkDisutility;
import routing.disutility.components.LinkStress;
import ch.sbb.matsim.analysis.TravelAttribute;
import ch.sbb.matsim.analysis.calc.GeometryCalculator;
import ch.sbb.matsim.analysis.data.GeometryData;
import ch.sbb.matsim.analysis.io.GeometryWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RouteComparison {

    private final static Logger log = Logger.getLogger(RouteComparison.class);
    private final static Integer SAMPLE_SIZE = 400;

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length < 4 | args.length == 5) {
            throw new RuntimeException("Program requires at least 5 arguments: \n" +
                    "(0) MATSim network file path (.xml) \n" +
                    "(1) Zone coordinates file (.csv) \n" +
                    "(2) Network geopackage file path (.gpkg) \n" +
                    "(3) Output file path (.gpkg) \n" +
                    "(4+) OPTIONAL: Names of zones to be used for routing");
        }

        String matsimNetworkFilePath = args[0];
        String zoneCoordinates = args[1];
        String gpkgNetworkFilePath = args[2];
        String outputFile = args[3];

        // Read network
        log.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(matsimNetworkFilePath);

        // Use walk-specific network
        Network walkNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(walkNetwork, Collections.singleton(TransportMode.walk));

        // Create zone-coord map and remove spaces
        Map<String, Coord> zoneCoordMap;
        zoneCoordMap = BuildZoneCoordMap(zoneCoordinates);

        // Determine set of zones to be used for routing
        Set<String> routingZones;
        if (args.length == 4) {
            log.info("Randomly sampling " + SAMPLE_SIZE + " of " + zoneCoordMap.size() + " zones.");
            List<String> routingZonesList = new ArrayList<>(zoneCoordMap.keySet());
            Collections.shuffle(routingZonesList);
            routingZones = routingZonesList.stream().limit(SAMPLE_SIZE).collect(Collectors.toSet());
        } else {
            routingZones = Arrays.stream(args).skip(4).
                    map(s -> s.replaceAll("\\s+", "")).
                    collect(Collectors.toSet());
            zoneCoordMap = zoneCoordMap.entrySet().stream().
                    filter(map -> routingZones.contains(map.getKey().replaceAll("\\s+", ""))).
                    collect(Collectors.toMap(map -> map.getKey().replaceAll("\\s+", ""), Map.Entry::getValue));
        }

        // Create zone-node map
        Map<String, Node> zoneNodeMap = BuildZoneNodeMap(zoneCoordMap,walkNetwork,walkNetwork);

        // CREATE VEHICLE & SET UP TRAVEL TIME
        Vehicle veh = null;
        TravelTime tt = new WalkTravelTime();

        // DEFINE TRAVEL DISUTILITIES HERE
        Map<String,TravelDisutility> travelDisutilities = new LinkedHashMap<>();

        // Shortest distance
        travelDisutilities.put("shortestDistance", new JibeWalkDisutility(null, tt, 0,1,
                0,0,0,0));

        // Fastest
        travelDisutilities.put("fastest", new JibeWalkDisutility(null, tt,1,0,
                0,0,0,0));

        // Jibe (daytime)
        travelDisutilities.put("jibeDay", new JibeWalkDisutility(false,tt));

        // Jibe (nighttime)
        travelDisutilities.put("jibeNight", new JibeWalkDisutility(true,tt));

        // DEFINE ADDITIONAL ROUTE ATTRIBUTES TO INCLUDE IN GPKG (DOES NOT AFFECT ROUTING)
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("vgvi",(l,td) -> LinkAttractiveness.getVgviFactor(l) * l.getLength());
        attributes.put("lighting",(l,td) -> LinkAttractiveness.getLightingFactor(l) * l.getLength());
        attributes.put("attractivenessDay", (l,td) -> LinkAttractiveness.getAttractiveness(l,false) * l.getLength());
        attributes.put("attractivenessNight", (l,td) -> LinkAttractiveness.getAttractiveness(l,true) * l.getLength());
        attributes.put("stress",(l,td) -> LinkStress.getWalkStress(l) * l.getLength());
        attributes.put("jctStress",(l,td) -> JctStress.getWalkJunctionStress(l));
        attributes.put("c_tot",(l,td) -> td.getLinkTravelDisutility(l,0,null, veh));
        attributes.put("c_time",(l,td) -> ((JibeWalkDisutility) td).getTimeComponent(l,0,null,veh));
        attributes.put("c_dist",(l,td) -> ((JibeWalkDisutility) td).getDistanceComponent(l));
        attributes.put("c_grad",(l,td) -> ((JibeWalkDisutility) td).getGradientComponent(l));
        attributes.put("c_attr",(l,td) -> ((JibeWalkDisutility) td).getAttractivenessComponent(l));
        attributes.put("c_stress",(l,td) -> ((JibeWalkDisutility) td).getStressComponent(l));
        attributes.put("c_jct",(l,td) -> ((JibeWalkDisutility) td).getJunctionComponent(l));

        // OUTPUT RESULTS
        if(outputFile.endsWith(".csv")) {

            // IF .CSV, CALCULATE ATTRIBUTES ONLY, DO NOT INCLUDE GEOMETRIES
            HashMap<String, IndicatorData> indicators = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating attributes for route " + e.getKey());
                IndicatorData<String> indicatorData = IndicatorCalculator.calculate(walkNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes, veh,14);
                indicators.put(e.getKey(),indicatorData);
            }
            IndicatorWriter.writeAsCsv(indicators,outputFile);
        } else if(outputFile.endsWith(".gpkg")) {

            // IF .GPKG, CALCULATE ATTRIBUTES AND GEOMETRIES
            HashMap<String, GeometryData> geometries = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating geometries for route " + e.getKey());
                GeometryData<String> routeData = GeometryCalculator.calculate(walkNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes,veh,14);
                geometries.put(e.getKey(),routeData);
            }
            GeometryWriter.writeGpkg(geometries,zoneNodeMap,gpkgNetworkFilePath,outputFile);
        } else {
            log.error("Unable to output results: please specify output file as .gpkg or .csv");
        }
    }

    public static Map<String, Coord> BuildZoneCoordMap(String filename) throws IOException {
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

    public static <T> Map<T, Node> BuildZoneNodeMap(Map<T, Coord> zoneCoordMap, Network xy2lNetwork, Network routingNetwork) {
        Map<T, Node> zoneNodeMap = new HashMap<>();
        for (Map.Entry<T, Coord> e : zoneCoordMap.entrySet()) {
            T zoneId = e.getKey();
            Coord coord = e.getValue();
            Node node = routingNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2lNetwork, coord).getToNode().getId());
            zoneNodeMap.put(zoneId, node);
        }
        return zoneNodeMap;
    }



}
