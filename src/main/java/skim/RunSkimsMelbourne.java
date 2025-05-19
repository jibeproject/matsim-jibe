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
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import routing.travelTime.WalkTravelTime;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunSkimsMelbourne {

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 4) {
            throw new RuntimeException("""
                    Program requires 4 arguments:\s
                    (0) Properties file\s
                    (1) Zone geometries (.gpkg)\s
                    (2) Zone ID attribute\s
                    (4) File path prefix""");
        }

        Resources.initializeResources(args[0]);
        String zonesFilename = args[1];
        String zoneIdAttribute = args[2];
        String filePathPrefix = args[3];

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
        Map<Integer,SimpleFeature> features = GpkgReader.readFeatures(new File(zonesFilename),zoneIdAttribute);

        // Initiate custom disutility
        SkimCalculator calc = new SkimCalculator(features);

        // Car skims
        calc.calculate("dist",networkCar,carXy2l,freespeed,new DistanceDisutility(),null);
        calc.calculate("free",networkCar,carXy2l,freespeed,freespeed,null);
        calc.calculate("congested",networkCar,carXy2l,congestedTime,congestedDisutility,null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "car.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Bike skims
        calc.calculate("dist",networkBike,networkBike,ttBike,new DistanceDisutility(),bike);
        calc.calculate("time",networkBike,networkBike,ttBike,new OnlyTimeDependentTravelDisutility(ttBike),bike);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "bike.omx",calc.getResults(),calc.getId2index());

        // Walk skims
        calc.calculate("dist",networkWalk,networkWalk,ttWalk,new DistanceDisutility(),null);
        calc.calculate("time",networkWalk,networkWalk,ttWalk,new OnlyTimeDependentTravelDisutility(ttWalk),null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "walk.omx",calc.getResults(),calc.getId2index());

        // PURPOSE-SPECIFIC MATRICES, FOR IMPLEMENTING IN MITO
        // Bike attributes
        List<RouteAttribute> bikeAttributes = new ArrayList<>();
        bikeAttributes.add(new RouteAttribute("grad", l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.)));
        bikeAttributes.add(new RouteAttribute("stressLink", l -> LinkStress.getStress(l,TransportMode.bike)));

        // Walk attributes
        List<RouteAttribute> walkAttributes = new ArrayList<>();
        walkAttributes.add(new RouteAttribute("vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
        walkAttributes.add(new RouteAttribute("speed", l -> Math.min(1.,((double) l.getAttributes().getAttribute("speedLimitMPH")) / 50.)));

        // Home-based Work (HBW)
        TravelDisutility tdBikeHBW = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {0, 1.1705777});
        TravelDisutility tdBikeHBW_f = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {0, 1.1705777 + 1.3119864});
        TravelDisutility tdWalkHBW = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0, 2.2560371});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBW,bike);
        calc.calculate("bike_female",networkBike,networkBike,ttBike,tdBikeHBW_f,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBW,null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "HBW.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based Education (HBE)
        TravelDisutility tdBikeHBE = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {65.8455067, 2.6375670});
        TravelDisutility tdWalkHBE = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0, 0.8270912});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBE,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBE,null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "HBE.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based recreation (HBR)
        TravelDisutility tdBikeHBR = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {8.7270880, 0});
        TravelDisutility tdBikeHBR_f = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {8.7270880 + 23.5710917, 0 + 1.7298508});
        TravelDisutility tdBikeHBR_c = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {8.7270880 + 51.9352371, 0 + 4.6070250});
        TravelDisutility tdWalkHBR = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0.6866997, 0.6779886});
        TravelDisutility tdWalkHBR_c = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0.6866997, 0.6779886 + 1.0379374});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBR,bike);
        calc.calculate("bike_female",networkBike,networkBike,ttBike,tdBikeHBR_f,bike);
        calc.calculate("bike_child",networkBike,networkBike,ttBike,tdBikeHBR_c,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBR,null);
        calc.calculate("walk_child",networkWalk,networkWalk,ttWalk,tdWalkHBR_c,null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "HBR.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based Shop & Other (HBSO)
        TravelDisutility tdBikeHBSO = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {331.2382835, 11.4359257});
        TravelDisutility tdWalkHBSO = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0, 0.3421390});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBSO,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkHBSO,null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "HBSO.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based Accompany (HBA)
        TravelDisutility tdBikeHBA = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {21.4115565, 0});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeHBA,bike);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "HBA.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Home-based Shop & Other (NHBW)
        TravelDisutility tdBikeNHBW = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {0, 3.9477647});
        TravelDisutility tdWalkNHBW = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0, 4.3210968});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeNHBW,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkNHBW,null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "NHBW.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // Non home-based other (NHBO)
        TravelDisutility tdBikeNHBO = new JibeDisutility4(networkBike,bike,"bike",ttBike,bikeAttributes, new double[] {0, 2.6660050});
        TravelDisutility tdWalkNHBO = new JibeDisutility4(networkWalk,null,"walk",ttWalk,walkAttributes, new double[] {0, 5.7158683});
        calc.calculate("bike",networkBike,networkBike,ttBike,tdBikeNHBO,bike);
        calc.calculate("walk",networkWalk,networkWalk,ttWalk,tdWalkNHBO,null);
        OmxWriter.createOmxSkimMatrix(filePathPrefix + "NHBO.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

    }
}
