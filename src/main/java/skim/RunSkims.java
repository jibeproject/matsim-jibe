package skim;

import gis.GpkgReader;
import io.OmxWriter;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import routing.Bicycle;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility2;
import routing.travelTime.WalkTravelTime;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RunSkims {

    private final static Logger logger = Logger.getLogger(RunSkims.class);

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
        new MatsimNetworkReader(networkCarInput).readFile(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));
        Network networkCar = NetworkUtils2.extractModeSpecificNetwork(networkCarInput, TransportMode.car);
        Network carXy2l = NetworkUtils2.extractXy2LinksNetwork(networkCar, l -> !((boolean) l.getAttributes().getAttribute("motorway")));

        // Create active mode networks
        Network network = NetworkUtils2.readFullNetwork();
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

        // CAR MATRICES
        calc.calculate("dist",networkCar,carXy2l,freespeed,new DistanceDisutility(),null);
        calc.calculate("time",networkCar,carXy2l,freespeed,freespeed,null);
        OmxWriter.createOmxSkimMatrix("skimCar.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // BIKE MATRICES
        TravelDisutility tdBikeCommute = new JibeDisutility2(networkBike,bike,"bike",ttBike,
                66.8,0.,6.3,0.);
        TravelDisutility tdBikeDiscretionary = new JibeDisutility2(networkBike,bike,"bike",ttBike,
                63.45,0.,1.59,0.);

        calc.calculate("commute",networkBike,networkBike,ttBike,tdBikeCommute,bike);
        calc.calculate("discretionary",networkBike,networkBike,ttBike,tdBikeDiscretionary,bike);
        calc.calculate("dist",networkBike,networkBike,ttBike,new DistanceDisutility(),bike);
        OmxWriter.createOmxSkimMatrix("skimBike.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();

        // WALK MATRICES
        TravelDisutility tdWalkCommute = new JibeDisutility2(networkWalk,null,"walk",ttWalk,
                0.,0.,0.,4.27);
        TravelDisutility tdWalkDiscretionary = new JibeDisutility2(networkWalk,null,"walk",ttWalk,
                0.,0.62,0.,14.34);

        calc.calculate("commute",networkWalk,networkWalk,ttWalk,tdWalkCommute,null);
        calc.calculate("discretionary",networkWalk,networkWalk,ttWalk,tdWalkDiscretionary,null);
        calc.calculate("dist",networkWalk,networkWalk,ttWalk,new DistanceDisutility(),null);
        OmxWriter.createOmxSkimMatrix("skimWalk.omx",calc.getResults(),calc.getId2index());
        calc.clearResults();
    }
}
