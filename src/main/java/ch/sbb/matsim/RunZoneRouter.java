package ch.sbb.matsim;

import accessibility.LocationData;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.matsim.api.core.v01.Id;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import resources.Resources;
import routing.ActiveAttributes;
import routing.Bicycle;
import routing.TravelAttribute;
import routing.disutility.DistanceDisutility;
import routing.travelTime.WalkTravelTime;
import ch.sbb.matsim.analysis.calc.IndicatorCalculator;
import ch.sbb.matsim.analysis.data.IndicatorData;
import ch.sbb.matsim.analysis.io.IndicatorWriter;
import routing.disutility.JibeDisutility;
import ch.sbb.matsim.analysis.calc.GeometryCalculator;
import ch.sbb.matsim.analysis.data.GeometryData;
import ch.sbb.matsim.analysis.io.GeometryWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RunZoneRouter {

    private final static Logger log = Logger.getLogger(RunZoneRouter.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length < 4 | args.length == 5) {
            throw new RuntimeException("Program requires at least 4 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Zone coordinates file (.csv) \n" +
                    "(2) Output file path (.csv or .gpkg) \n" +
                    "(3) Mode (walk or bike) \n" +
                    "(4+) OPTIONAL: Names of zones to be used for routing");
        }

        Resources.initializeResources(args[0]);
        String zoneCoordinates = args[1];
        String outputFile = args[2];
        String mode = args[3];

        // Read network
        Network modeNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

        // Create zone-node map and remove spaces
        LocationData zones = new LocationData("zones",zoneCoordinates, GpkgReader.readRegionBoundary());
        zones.estimateNetworkNodes(modeNetwork);
        Map<String,Id<Node>> zoneNodeMap = zones.getNodeIdMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue,Map.Entry::getKey));

        // Determine set of zones to be used for routing (subset if given as additional arguments)
        Set<String> routingZones;
        if (args.length == 4) {
            routingZones = zoneNodeMap.keySet();
        } else {
            routingZones = Arrays.stream(args).skip(5).
                    map(s -> s.replaceAll("\\s+", "")).
                    collect(Collectors.toSet());
            zoneNodeMap = zoneNodeMap.entrySet().stream().
                    filter(map -> routingZones.contains(map.getKey().replaceAll("\\s+", ""))).
                    collect(Collectors.toMap(map -> map.getKey().replaceAll("\\s+", ""), Map.Entry::getValue));
        }

        // CREATE VEHICLE & SET UP TRAVEL TIME
        Vehicle veh;
        TravelTime tt;
        if(mode.equals(TransportMode.walk)) {
            veh = null;
            tt = new WalkTravelTime();
        } else if (mode.equals(TransportMode.bike)) {
            Bicycle bicycle = new Bicycle(null);
            veh = bicycle.getVehicle();
            tt = bicycle.getTravelTime();
        } else {
            throw new RuntimeException("Routing not set up for mode " + mode);
        }

        // DEFINE TRAVEL DISUTILITIES HERE
        Map<String,TravelDisutility> travelDisutilities = new LinkedHashMap<>();
        travelDisutilities.put("short", new DistanceDisutility());
        travelDisutilities.put("fast", new OnlyTimeDependentTravelDisutility(tt));
        travelDisutilities.put("jibe", new JibeDisutility(mode,tt));


        // Run for testing multiple ambience/stress/junction costs
/*        for(int i = 0 ; i <= 4 ; i++) {
            for(int j = 0 ; j <= 2 ; j++) {
                    String name = "t_" + i + "_" + j;
                    travelDisutilities.put(name,new JibeDisutility(MODE, tt, MARGINAL_COST_TIME,MARGINAL_COST_DISTANCE,
                            marginalCostOfGradient,marginalCostOfSurfaceComfort,
                            i*1e-3,j*1e-3));
            }
        }*/

        // DEFINE ADDITIONAL ROUTE ATTRIBUTES TO INCLUDE IN GPKG (DOES NOT AFFECT ROUTING)
        LinkedHashMap<String, TravelAttribute> attributes = ActiveAttributes.getJibe(mode,veh);

        // OUTPUT RESULTS
        if(outputFile.endsWith(".csv")) {

            // IF .CSV, CALCULATE ATTRIBUTES ONLY, DO NOT INCLUDE GEOMETRIES
            HashMap<String, IndicatorData> indicators = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating attributes for route " + e.getKey());
                IndicatorData<String> indicatorData = IndicatorCalculator.calculate(modeNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes, veh);
                indicators.put(e.getKey(),indicatorData);
            }
            IndicatorWriter.writeAsCsv(indicators,outputFile);
        } else if(outputFile.endsWith(".gpkg")) {

            // IF .GPKG, CALCULATE ATTRIBUTES AND GEOMETRIES
            HashMap<String, GeometryData> geometries = new HashMap<>(travelDisutilities.size());
            for(Map.Entry<String,TravelDisutility> e : travelDisutilities.entrySet()) {
                log.info("Calculating geometries for route " + e.getKey());
                GeometryData<String> routeData = GeometryCalculator.calculate(modeNetwork,routingZones,routingZones,
                        zoneNodeMap,tt,e.getValue(),attributes,veh);
                geometries.put(e.getKey(),routeData);
            }
            GeometryWriter.writeGpkg(modeNetwork,geometries,zoneNodeMap,outputFile);
        } else {
            log.error("Unable to output results: please specify output file as .gpkg or .csv");
        }
    }
}
