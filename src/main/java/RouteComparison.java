import bicycle.WalkTravelTime;
import bicycle.speed.BicycleLinkSpeedCalculatorDefaultImpl;
import bicycle.BicycleTravelTime;
import ch.sbb.matsim.analysis.calc.IndicatorCalculator;
import ch.sbb.matsim.analysis.data.IndicatorData;
import ch.sbb.matsim.analysis.io.IndicatorWriter;
import bicycle.jibe.JibeCycleRoute;
import bicycle.jibe.CustomUtilityUtils;
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
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
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
    private final static boolean WALK = true;

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length < 4 | args.length == 5) {
            throw new RuntimeException("Program requires at least 4 arguments: \n" +
                    "(0) MATSim network file path \n" +
                    "(1) Zone coordinates file \n" +
                    "(2) Edges file path \n" +
                    "(3) Output file path \n" +
                    "(4+) OPTIONAL: Names of zones to be used for routing");
        }

        String networkFilePath = args[0];
        String zoneCoordinates = args[1];
        String edgesFilePath = args[2];
        String outputFile = args[3];


        // Setup config
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setMarginalUtilityOfInfrastructure_m(-0.0002);
        bicycleConfigGroup.setMarginalUtilityOfComfort_m(-0.0002);
        bicycleConfigGroup.setMarginalUtilityOfGradient_m_100m(-0.02);
        bicycleConfigGroup.setBicycleMode("bike");
        config.addModule(bicycleConfigGroup);
        PlanCalcScoreConfigGroup planCalcScoreConfigGroup = new PlanCalcScoreConfigGroup();
        log.info("Marginal utility of Money = " + planCalcScoreConfigGroup.getMarginalUtilityOfMoney());

        // Read network
        log.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFilePath);

        // Use mode-specific network
        String mode;
        if(WALK) {
            mode = TransportMode.walk;
        } else {
            mode = TransportMode.bike;
        }
        Network modeNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(modeNetwork, Collections.singleton(mode));

        // Create zone-coord map and remove spaces
        Map<String, Coord> zoneCoordMap;
        zoneCoordMap = CalculateData.buildZoneCoordMap(zoneCoordinates);

        Set<String> routingZones;
        if (args.length == 4) {
            routingZones = zoneCoordMap.keySet();
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

        // CREATE VEHICLE
        Vehicle veh;
        TravelTime tt;
        if(WALK) {
            veh = null;
            tt = new WalkTravelTime();
        } else {
            VehicleType type = VehicleUtils.createVehicleType(Id.create("bicycle", VehicleType.class));
            type.setMaximumVelocity(MAX_BIKE_SPEED);
            BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
            veh = VehicleUtils.createVehicle(Id.createVehicleId(1), type);
            tt = new BicycleTravelTime(linkSpeedCalculator);
        }


        // TRAVEL TIMES


        // Get marginal cost of time
        PlanCalcScoreConfigGroup.ModeParams bicycleParams = planCalcScoreConfigGroup.getModes().get(bicycleConfigGroup.getBicycleMode());
        double marginalCostOfTime_s = -(bicycleParams.getMarginalUtilityOfTraveling() / 3600.0) + planCalcScoreConfigGroup.getPerforming_utils_hr() / 3600.0;
        double marginalCostOfDistance_m = -(bicycleParams.getMonetaryDistanceRate() * planCalcScoreConfigGroup.getMarginalUtilityOfMoney())
                - bicycleParams.getMarginalUtilityOfDistance();
        double marginalCostOfGradient_m_100m = -(bicycleConfigGroup.getMarginalUtilityOfGradient_m_100m());
        double marginalCostOfComfort_m = -(bicycleConfigGroup.getMarginalUtilityOfComfort_m());


        log.info("Marginal cost of time (s): " + marginalCostOfTime_s);
        log.info("Marginal cost of distance (m): " + marginalCostOfDistance_m);
        log.info("Marginal cost of gradient (m/100m): " + marginalCostOfGradient_m_100m);
        log.info("Marginal cost of surface comfort (m): " + marginalCostOfComfort_m);

        // DEFINE TRAVEL DISUTILITIES HERE
        Map<String,TravelDisutility> travelDisutilities = new LinkedHashMap<>();

        // Shortest distance
        travelDisutilities.put("shortestDistance", new JibeCycleRoute(tt, 0,1,
                0,0,0,0,0));

        // Fastest
        travelDisutilities.put("fastest", new JibeCycleRoute(tt,1,0,
                0,0,0,0,0));

        for(int i = 0 ; i < 5 ; i = i+2) {
            for(int j = 0 ; j < 5 ; j = j+2) {
                for(int k = 0 ; k < 5 ; k = k+2) {
                    String name = "t_" + i + "_" + j + "_" + k;
                    travelDisutilities.put(name,new JibeCycleRoute(tt, marginalCostOfTime_s,marginalCostOfDistance_m,
                            marginalCostOfGradient_m_100m,marginalCostOfComfort_m,
                            i*1e-3,j*1e-3,k*1e-4));
                }
            }
        }

        // DEFINE ADDITIONAL ROUTE ATTRIBUTES TO INCLUDE IN GPKG (DOES NOT AFFECT ROUTING)
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("vgvi",(l,td) -> CustomUtilityUtils.getVgviFactor(l) * l.getLength());
        attributes.put("lighting",(l,td) -> CustomUtilityUtils.getLightingFactor(l) * l.getLength());
        attributes.put("shannon", (l,td) -> CustomUtilityUtils.getShannonFactor(l) * l.getLength());
        attributes.put("crime", (l,td) -> CustomUtilityUtils.getCrimeFactor(l) * l.getLength());
        attributes.put("POIs",(l,td) -> CustomUtilityUtils.getPoiFactor(l) * l.getLength());
        attributes.put("negPOIs",(l,td) -> CustomUtilityUtils.getNegativePoiFactor(l) * l.getLength());
        attributes.put("freightPOIs",(l,td) -> CustomUtilityUtils.getFreightPoiFactor(l) * l.getLength());
        attributes.put("attractiveness", (l,td) -> CustomUtilityUtils.getDayAttractiveness(l) * l.getLength());
        if(WALK) {
            attributes.put("stress",(l,td) -> CustomUtilityUtils.getWalkStress(l) * l.getLength());
            attributes.put("jctStress",(l,td) -> CustomUtilityUtils.getWalkJunctionStress(l));
        } else {
            attributes.put("stress",(l,td) -> CustomUtilityUtils.getCycleStress(l) * l.getLength());
            attributes.put("jctStress",(l,td) -> CustomUtilityUtils.getCycleJunctionStress(l));
        }
        attributes.put("c_tot",(l,td) -> td.getLinkTravelDisutility(l,0,null, veh));
        attributes.put("c_time",(l,td) -> ((JibeCycleRoute) td).getTimeComponent(l,0,null,veh));
        attributes.put("c_dist",(l,td) -> ((JibeCycleRoute) td).getDistanceComponent(l));
        attributes.put("c_grad",(l,td) -> ((JibeCycleRoute) td).getGradientComponent(l));
        attributes.put("c_surf",(l,td) -> ((JibeCycleRoute) td).getSurfaceComponent(l));
        attributes.put("c_attr",(l,td) -> ((JibeCycleRoute) td).getAttractivenessComponent(l));
        attributes.put("c_stress",(l,td) -> ((JibeCycleRoute) td).getStressComponent(l));
        attributes.put("c_jct",(l,td) -> ((JibeCycleRoute) td).getJunctionComponent(l));


        // FOR CALCULATING ATTRIBUTES ONLY
        if(outputFile.endsWith(".csv")) {
            HashMap<String, IndicatorData> indicators = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating geometries for route " + e.getKey());
                IndicatorData<String> indicatorData = IndicatorCalculator.calculate(modeNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes, veh,10);
                indicators.put(e.getKey(),indicatorData);
            }
            IndicatorWriter.writeAsCsv(indicators,outputFile);
        } else if(outputFile.endsWith(".gpkg")) {
            HashMap<String, GeometryData> geometries = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating geometries for route " + e.getKey());
                GeometryData<String> routeData = GeometryCalculator.calculate(modeNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes,veh,10);
                geometries.put(e.getKey(),routeData);
            }
            GeometryWriter.writeGpkg(geometries,zoneNodeMap,edgesFilePath,outputFile);
        } else {
            log.error("Unable to output results: please specify output file as .gpkg or .csv");
        }
    }
}
