import routing.travelTime.WalkTravelTime;
import routing.utility.LinkAttractiveness;
import routing.utility.JctStress;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;
import routing.travelTime.BicycleTravelTime;
import ch.sbb.matsim.analysis.calc.IndicatorCalculator;
import ch.sbb.matsim.analysis.data.IndicatorData;
import ch.sbb.matsim.analysis.io.IndicatorWriter;
import routing.JibeDisutility;
import routing.utility.LinkStress;
import ch.sbb.matsim.analysis.CalculateData;
import ch.sbb.matsim.analysis.TravelAttribute;
import ch.sbb.matsim.analysis.calc.GeometryCalculator;
import ch.sbb.matsim.analysis.data.GeometryData;
import ch.sbb.matsim.analysis.io.GeometryWriter;
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
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.opengis.referencing.FactoryException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RouteComparison {

    private final static Logger log = Logger.getLogger(RouteComparison.class);
    private final static double MAX_BIKE_SPEED = 16 / 3.6;
    private final static Integer SAMPLE_SIZE = 400;

    private final static double MARGINAL_COST_TIME = 2 / 300.; // seconds
    private final static double MARGINAL_COST_DISTANCE = 0.; // metres
    private final static double MARGINAL_COST_GRADIENT = 0.02; // m/100m
    private final static double MARGINAL_COST_SURFACE = 2e-4;
    private final static double MARGINAL_COST_ATTRACTIVENESS = 6e-3;
    private final static double MARGINAL_COST_STRESS = 6e-3;
    private final static double JUNCTION_EQUIVALENT_LENGTH = 10.; // in meters
    private final static String MODE = TransportMode.walk;

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length < 4 | args.length == 5) {
            throw new RuntimeException("Program requires at least 4 arguments: \n" +
                    "(0) MATSim network file path (.xml) \n" +
                    "(1) Zone coordinates file (.csv) \n" +
                    "(2) Edges file path (.gpkg) \n" +
                    "(3) Output file path (.gpkg) \n" +
                    "(4+) OPTIONAL: Names of zones to be used for routing");
        }

        String networkFilePath = args[0];
        String zoneCoordinates = args[1];
        String edgesFilePath = args[2];
        String outputFile = args[3];


        // Setup config
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode("bike");
        config.addModule(bicycleConfigGroup);

        // Read network
        log.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFilePath);

        // Use mode-specific network
        Network modeNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(modeNetwork, Collections.singleton(MODE));

        // Create zone-coord map and remove spaces
        Map<String, Coord> zoneCoordMap;
        zoneCoordMap = CalculateData.buildZoneCoordMap(zoneCoordinates);

        // Determine set of zones to be used for routing
        Set<String> routingZones;
        if (args.length == 4) {
            if(SAMPLE_SIZE != null) {
                log.info("Randomly sampling " + SAMPLE_SIZE + " of " + zoneCoordMap.size() + " zones.");
                List<String> routingZonesList = new ArrayList<>(zoneCoordMap.keySet());
                Collections.shuffle(routingZonesList);
                routingZones = routingZonesList.stream().limit(SAMPLE_SIZE).collect(Collectors.toSet());
            } else {
                routingZones = zoneCoordMap.keySet();
            }
        } else {
            routingZones = Arrays.stream(args).skip(4).
                    map(s -> s.replaceAll("\\s+", "")).
                    collect(Collectors.toSet());
            zoneCoordMap = zoneCoordMap.entrySet().stream().
                    filter(map -> routingZones.contains(map.getKey().replaceAll("\\s+", ""))).
                    collect(Collectors.toMap(map -> map.getKey().replaceAll("\\s+", ""), Map.Entry::getValue));
        }

        // Create zone-node map
        Map<String, Node> zoneNodeMap = CalculateData.buildZoneNodeMap(zoneCoordMap,modeNetwork,modeNetwork);

        // CREATE VEHICLE & SET UP TRAVEL TIME
        Vehicle veh;
        TravelTime tt;
        if(MODE.equals(TransportMode.walk)) {
            veh = null;
            tt = new WalkTravelTime();
        } else if (MODE.equals(TransportMode.bike)) {
            VehicleType type = VehicleUtils.createVehicleType(Id.create("routing", VehicleType.class));
            type.setMaximumVelocity(MAX_BIKE_SPEED);
            BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
            veh = VehicleUtils.createVehicle(Id.createVehicleId(1), type);
            tt = new BicycleTravelTime(linkSpeedCalculator);
        } else {
            throw new RuntimeException("Routing not set up for mode " + MODE);
        }

        log.info("Marginal cost of time (s): " + MARGINAL_COST_TIME);
        log.info("Marginal cost of distance (m): " + MARGINAL_COST_DISTANCE);
        log.info("Marginal cost of gradient (m/100m): " + MARGINAL_COST_GRADIENT);
        log.info("Marginal cost of surface comfort (m): " + MARGINAL_COST_SURFACE);
        log.info("Marginal cost of attractiveness (m): " + MARGINAL_COST_ATTRACTIVENESS);
        log.info("Marginal cost of stress (m): " + MARGINAL_COST_STRESS);

        // DEFINE TRAVEL DISUTILITIES HERE
        Map<String,TravelDisutility> travelDisutilities = new LinkedHashMap<>();

        // Shortest distance
        travelDisutilities.put("shortestDistance", new JibeDisutility(MODE, tt, 0,1,
                0,0,0,0,0));

        // Fastest
        travelDisutilities.put("fastest", new JibeDisutility(MODE, tt,1,0,
                0,0,0,0,0));

        // Jibe
        travelDisutilities.put("jibe", new JibeDisutility(MODE,tt,MARGINAL_COST_TIME,MARGINAL_COST_DISTANCE,
                MARGINAL_COST_GRADIENT,MARGINAL_COST_SURFACE,MARGINAL_COST_ATTRACTIVENESS,MARGINAL_COST_STRESS,
                MARGINAL_COST_STRESS*JUNCTION_EQUIVALENT_LENGTH));


        // Run for testing multiple attractiveness/stress/junction costs
/*        for(int i = 0 ; i <= 4 ; i++) {
            for(int j = 0 ; j <= 2 ; j++) {
                for(int k = 0 ; k <= 10 ; k = k+2) {
                    String name = "t_" + i + "_" + j + "_" + k;
                    travelDisutilities.put(name,new JibeDisutility(MODE, tt, MARGINAL_COST_TIME,MARGINAL_COST_DISTANCE,
                            marginalCostOfGradient,marginalCostOfSurfaceComfort,
                            i*1e-3,j*1e-3,k*1e-2));
                }
            }
        }*/

        // DEFINE ADDITIONAL ROUTE ATTRIBUTES TO INCLUDE IN GPKG (DOES NOT AFFECT ROUTING)
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("vgvi",(l,td) -> LinkAttractiveness.getVgviFactor(l) * l.getLength());
        attributes.put("lighting",(l,td) -> LinkAttractiveness.getLightingFactor(l) * l.getLength());
        attributes.put("shannon", (l,td) -> LinkAttractiveness.getShannonFactor(l) * l.getLength());
        attributes.put("crime", (l,td) -> LinkAttractiveness.getCrimeFactor(l) * l.getLength());
        attributes.put("POIs",(l,td) -> LinkAttractiveness.getPoiFactor(l) * l.getLength());
        attributes.put("negPOIs",(l,td) -> LinkAttractiveness.getNegativePoiFactor(l) * l.getLength());
        attributes.put("freightPOIs",(l,td) -> LinkStress.getFreightPoiFactor(l) * l.getLength());
        attributes.put("attractiveness", (l,td) -> LinkAttractiveness.getDayAttractiveness(l) * l.getLength());
        attributes.put("stress",(l,td) -> LinkStress.getStress(l,MODE) * l.getLength());
        attributes.put("jctStress",(l,td) -> JctStress.getJunctionStress(l,MODE));
        attributes.put("c_tot",(l,td) -> td.getLinkTravelDisutility(l,0,null, veh));
        attributes.put("c_time",(l,td) -> ((JibeDisutility) td).getTimeComponent(l,0,null,veh));
        attributes.put("c_dist",(l,td) -> ((JibeDisutility) td).getDistanceComponent(l));
        attributes.put("c_grad",(l,td) -> ((JibeDisutility) td).getGradientComponent(l));
        attributes.put("c_surf",(l,td) -> ((JibeDisutility) td).getSurfaceComponent(l));
        attributes.put("c_attr",(l,td) -> ((JibeDisutility) td).getAttractivenessComponent(l));
        attributes.put("c_stress",(l,td) -> ((JibeDisutility) td).getStressComponent(l));
        attributes.put("c_jct",(l,td) -> ((JibeDisutility) td).getJunctionComponent(l));


        // OUTPUT RESULTS
        if(outputFile.endsWith(".csv")) {

            // IF .CSV, CALCULATE ATTRIBUTES ONLY, DO NOT INCLUDE GEOMETRIES
            HashMap<String, IndicatorData> indicators = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating attributes for route " + e.getKey());
                IndicatorData<String> indicatorData = IndicatorCalculator.calculate(modeNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes, veh,14);
                indicators.put(e.getKey(),indicatorData);
            }
            IndicatorWriter.writeAsCsv(indicators,outputFile);
        } else if(outputFile.endsWith(".gpkg")) {

            // IF .GPKG, CALCULATE ATTRIBUTES AND GEOMETRIES
            HashMap<String, GeometryData> geometries = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating geometries for route " + e.getKey());
                GeometryData<String> routeData = GeometryCalculator.calculate(modeNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes,veh,14);
                geometries.put(e.getKey(),routeData);
            }
            GeometryWriter.writeGpkg(geometries,zoneNodeMap,edgesFilePath,outputFile);
        } else {
            log.error("Unable to output results: please specify output file as .gpkg or .csv");
        }
    }
}
