package skim;

import demand.volumes.DailyVolumeEventHandler;
import estimation.RouteAttribute;
import gis.GpkgReader;
import io.OmxWriter;
import network.NetworkUtils2;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import routing.Bicycle;
import routing.Gradient;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility4;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import routing.travelTime.WalkTravelTime;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RunSkims {

    public static final String FILE_PATH_PREFIX = "skims/";

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Zone geometries (.gpkg)");
        }

        Resources.initializeResources(args[0]);
        String zonesFilename = args[1];

        // Create bicycle travel time
        Config config = ConfigUtils.createConfig();
        Bicycle bicycle = new Bicycle(config);
        Vehicle bike = bicycle.getVehicle();

        // Create car networks
        Network networkCarInput = NetworkUtils.createNetwork();
        new MatsimNetworkReader(networkCarInput).readFile(Resources.instance.getString(Properties.MATSIM_DEMAND_OUTPUT_NETWORK));
        Network networkCar = NetworkUtils2.extractModeSpecificNetwork(networkCarInput, TransportMode.car);
        Network carXy2l = NetworkUtils2.extractXy2LinksNetwork(networkCar, l -> !((boolean) l.getAttributes().getAttribute("motorway")));

        // Car freespeed & congested travel time
        String tfgmDemandEvents = Resources.instance.getString(Properties.MATSIM_DEMAND_OUTPUT_EVENTS);
        TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(networkCar);
        TravelTimeCalculator congested = builder.build();
        DailyVolumeEventHandler dailyVolumeEventHandler = new DailyVolumeEventHandler(Resources.instance.getString(Properties.MATSIM_DEMAND_OUTPUT_VEHICLES));
        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(congested);
        events.addHandler(dailyVolumeEventHandler);
        (new MatsimEventsReader(events)).readFile(tfgmDemandEvents);
        TravelTime congestedTime = congested.getLinkTravelTimes();
        TravelDisutility congestedDisutility = new OnlyTimeDependentTravelDisutility(congested.getLinkTravelTimes());

        // Create active mode networks
        Network network = NetworkUtils2.readFullNetwork();
        NetworkUtils2.addSimulationVolumes(dailyVolumeEventHandler,network);
        NetworkUtils2.addCrossingAttributes(network);
        Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);
        Network networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);

        // Travel time functions
        FreespeedTravelTimeAndDisutility freespeed = new FreespeedTravelTimeAndDisutility(-1,0,0);
        TravelTime ttBike = bicycle.getTravelTime();
        TravelTime ttWalk = new WalkTravelTime();

        // Create zone-coord map and remove spaces
        Map<Integer,SimpleFeature> features = GpkgReader.readFeatures(new File(zonesFilename),"zoneID");

        // Initiate custom disutility
        SkimCalculator calc = new SkimCalculator(features);

        // Car skims
        calc.calculate("dist",networkCar,carXy2l,freespeed,new DistanceDisutility(),null);
        calc.calculate("free",networkCar,carXy2l,freespeed,freespeed,null);
        calc.calculate("congested",networkCar,carXy2l,congestedTime,congestedDisutility,null);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "skimCar.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Bike skims
        calc.calculate("dist",networkBike,networkBike,ttBike,new DistanceDisutility(),bike);
        calc.calculate("time",networkBike,networkBike,ttBike,new OnlyTimeDependentTravelDisutility(ttBike),bike);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "skimBike.omx",calc.getResults(),calc.getId2index());

        // Walk skims
        calc.calculate("dist",networkWalk,networkWalk,ttWalk,new DistanceDisutility(),null);
        calc.calculate("time",networkWalk,networkWalk,ttWalk,new OnlyTimeDependentTravelDisutility(ttWalk),null);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "skimWalk.omx",calc.getResults(),calc.getId2index());

        // PURPOSE-SPECIFIC MATRICES, FOR IMPLEMENTING IN MITO
        // Bike attributes
        List<RouteAttribute> bikeAttributes = new ArrayList<>();
        bikeAttributes.add(new RouteAttribute("grad", l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.)));
        bikeAttributes.add(new RouteAttribute("stressLink", l -> LinkStress.getStress(l,TransportMode.bike)));

        // Walk attributes
        List<RouteAttribute> walkAttributes = new ArrayList<>();
        walkAttributes.add(new RouteAttribute("vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
        walkAttributes.add(new RouteAttribute("speed", l -> Math.min(1.,l.getFreespeed() / 22.35)));
        walkAttributes.add(new RouteAttribute("stressJct", l -> JctStress.getStressProp(l,TransportMode.walk)));

        // Home-based Work (HBW)
        TravelDisutility tdBikeHBW = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {35.9032908,2.3084587});
        TravelDisutility tdBikeHBW_f = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {35.9032908,2.3084587 + 2.7762033});
        TravelDisutility tdWalkHBW = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0.3307472,0,4.9887390});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBW,bike);
        calc.calculate("bike_female",networkBike,networkBike,ttBike,tdBikeHBW_f,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBW,null);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "HBW.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based Education (HBE)
        TravelDisutility tdBikeHBE = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {0,4.3075357});
        TravelDisutility tdWalkHBE = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0,0,1.0037846});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBE,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBE,null);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "HBE.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based Discretionary (HBD)
        TravelDisutility tdBikeHBD = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {57.0135325,1.2411983});
        TravelDisutility tdBikeHBD_c = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {57.0135325,1.2411983 + 6.4243251});
        TravelDisutility tdWalkHBD = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0.7789561,0.4479527,5.8219067});
        TravelDisutility tdWalkHBD_c = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0.7789561,0.4479527 + 2.0418898,5.8219067});
        TravelDisutility tdWalkHBD_o = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0.7789561,0.4479527 + 0.3715017,5.8219067});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBD,bike);
        calc.calculate("bike_child",networkBike,networkBike,ttBike,tdBikeHBD_c,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBD,null);
        calc.calculate("walk_child",networkWalk,networkWalk,ttWalk,tdWalkHBD_c,null);
        calc.calculate("walk_elderly",networkWalk,networkWalk,ttWalk,tdWalkHBD_o,null);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "HBD.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based Accompany (HBA)
        TravelDisutility tdWalkHBA = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0.6908324,0,0});
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBA,null);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "HBA.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Non home-based other (NHBO)
        TravelDisutility tdWalkNHBO = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0,3.4485883,0});
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkNHBO,null);
        OmxWriter.createOmxSkimMatrix(FILE_PATH_PREFIX + "NHBO.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

    }
}
