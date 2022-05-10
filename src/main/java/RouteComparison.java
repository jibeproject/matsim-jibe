import bicycle.BicycleLinkSpeedCalculatorDefaultImpl;
import bicycle.BicycleTravelDisutility;
import bicycle.BicycleTravelTime;
import bicycle.BicycleUtilityUtils;
import bicycle.jibe.CustomBicycleDisutility;
import bicycle.jibe.CustomBicycleUtils;
import bicycle.jibe.SafeRoute;
import ch.sbb.matsim.analysis.CalculateData;
import ch.sbb.matsim.analysis.TravelAttribute;
import ch.sbb.matsim.analysis.calc.GeometryCalculator;
import ch.sbb.matsim.analysis.data.GeometryData;
import ch.sbb.matsim.analysis.io.GeometryWriter;
import disutility.DistanceAsTravelDisutility;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
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
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.opengis.referencing.FactoryException;

import static bicycle.jibe.CycleSafety.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RouteComparison {

    private final static Logger log = Logger.getLogger(RouteComparison.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length < 6) {
            throw new RuntimeException("Program requires at least 6 arguments: \n" +
                    "(0) MATSim network file path \n" +
                    "(1) Zone coordinates file \n" +
                    "(2) Edges file path \n" +
                    "(3) Output file path \n" +
                    "(4) Zone 1 \n" +
                    "(5) Zone 2 \n" +
                    "(6+) Additional zones for routing");
        }

        String networkFilePath = args[0];
        String zoneCoordinates = args[1];
        String edgesFilePath = args[2];
        String outputFilePath = args[3];
        Set<String> routingZones = Arrays.stream(args).skip(4).
                map(s -> s.replaceAll("\\s+", "")).
                collect(Collectors.toSet());

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

        // Create cycle-specific network
        Network networkBike = extractModeSpecificNetwork(network, TransportMode.bike);

        // Create zone-coord map and remove spaces
        Map<String, Coord> zoneCoordMap = CalculateData.buildZoneCoordMap(zoneCoordinates).entrySet().stream().
                filter(map -> routingZones.contains(map.getKey().replaceAll("\\s+", ""))).
                collect(Collectors.toMap(map -> map.getKey().replaceAll("\\s+", ""), map -> map.getValue()));

        // Create zone-node map
        Map<String, Node> zoneNodeMap = CalculateData.buildZoneNodeMap(zoneCoordMap,networkBike,networkBike);


        // TRAVEL TIMES
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        TravelTime ttCycle = new BicycleTravelTime(linkSpeedCalculator);

        // DEFINE TRAVEL DISUTILITIES HERE
        Map<String,TravelDisutility> travelDisutilities = new HashMap<>();

        // Shortest distance
        travelDisutilities.put("shortestDistance", new DistanceAsTravelDisutility());

        // Least time
        travelDisutilities.put("fastest", new OnlyTimeDependentTravelDisutility(ttCycle));

        // Berlin disutility (default from bicycle MATSim contrib)
        TravelDisutility tdBerlin = new BicycleTravelDisutility((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME),
                planCalcScoreConfigGroup, ttCycle);

        log.info("Marginal costs for TU Berlin cycle algorithm:");
        ((BicycleTravelDisutility) tdBerlin).printDefaultParams(log);

        travelDisutilities.put("berlin", tdBerlin);

        // JIBE custom disutility
        TravelDisutility tdJibe = new CustomBicycleDisutility((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME),
                planCalcScoreConfigGroup, ttCycle);

        log.info("Marginal costs for JIBE cycle algorithm:");
        ((CustomBicycleDisutility) tdJibe).printMarginalCosts(log);

        travelDisutilities.put("jibe", tdJibe);

        // Green only
        TravelDisutility greenOnly = new SafeRoute((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME),
                planCalcScoreConfigGroup, ttCycle, 100, 100);

        travelDisutilities.put("green", greenOnly);

        // DEFINE ADDITIONAL ROUTE ATTRIBUTES TO INCLUDE IN GPKG (DOES NOT AFFECT ROUTING)
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("infrastructureFactor",l -> CustomBicycleUtils.getInfrastructureFactor(l) * l.getLength());
        attributes.put("gradientFactor", l -> BicycleUtilityUtils.getGradientFactor(l) * l.getLength());
        attributes.put("comfortFactor", l -> BicycleUtilityUtils.getComfortFactor(l) * l.getLength());
        attributes.put("avgTrafficSpeed",l -> (double) l.getAttributes().getAttribute("trafficSpeedKPH") * l.getLength());
        attributes.put("ndvi",l -> CustomBicycleUtils.getNdviFactor(l) * l.getLength());
        attributes.put("prop_green",l -> CustomBicycleUtils.getLinkSafety(l).equals(GREEN) ? l.getLength() : 0.);
        attributes.put("prop_amber",l -> CustomBicycleUtils.getLinkSafety(l).equals(AMBER) ? l.getLength() : 0.);
        attributes.put("prop_red",l -> CustomBicycleUtils.getLinkSafety(l).equals(RED) ? l.getLength() : 0.);

        // RUN ROUTING ALGORITHMS AND STORE EDGE IDS
        HashMap<String, GeometryData> geometries = new HashMap<>(travelDisutilities.size());
        for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
            log.info("Calculating geometries for route " + e.getKey());
            GeometryData<String> routeData = GeometryCalculator.calculate(networkBike,routingZones,routingZones,
                    zoneNodeMap,ttCycle,e.getValue(),attributes, 10);
            geometries.put(e.getKey(),routeData);
        }

        // WRITE TO GPKG
        GeometryWriter.writeGpkg(geometries,zoneNodeMap,edgesFilePath,outputFilePath);
    }

    private static Network extractModeSpecificNetwork(Network network, String transportMode) {
        Network modeSpecificNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(modeSpecificNetwork, Collections.singleton(transportMode));
        //NetworkUtils.runNetworkCleaner(modeSpecificNetwork);
        return modeSpecificNetwork;
    }
}
