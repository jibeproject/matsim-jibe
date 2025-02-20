package routing.detour;

import gis.GpkgReader;
import io.ioUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import resources.Resources;
import routing.Bicycle;
import routing.disutility.JibeDisutility3;
import routing.travelTime.WalkTravelTime;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RunNodeDetourCalculator {

    public static final Logger log = Logger.getLogger(RunNodeDetourCalculator.class);
    private final static double MAX_MC_AMBIENCE = 5;
    private final static double MAX_MC_STRESS = 5;
    private final static int AMBIENCE_SAMPLES = 5;
    private final static int STRESS_SAMPLES = 5;

    public static void main(String[] args) throws IOException {
        if(args.length != 4) {
            throw new RuntimeException("Program requires 3 argument: \n" +
                    "(0) General Properties file\n" +
                    "(1) Output densities CSV file\n" +
                    "(2) Large detours CSV file\n" +
                    "(3) Mode");
        }

        Resources.initializeResources(args[0]);
        String densitiesOutputFile = args[1];
        String largeDetoursOutputFile = args[2];
        String mode = args[3];

        // Read mode-specific network
        Network network = NetworkUtils2.readModeSpecificNetwork(mode);

        // Travel time and vehicle
        TravelTime tt;
        Vehicle veh;

        if(mode.equals(TransportMode.bike)) {
            Bicycle bicycle = new Bicycle(null);
            tt = bicycle.getTravelTime();
            veh = bicycle.getVehicle();
        } else if (mode.equals(TransportMode.walk)) {
            tt = new WalkTravelTime();
            veh = null;
        } else throw new RuntimeException("Modes other than walk and bike are not supported!");

        // Get origin nodes
        log.info("Identifying nodes within boundary...");
        Geometry regionBoundary = GpkgReader.readRegionBoundary();
        Set<Id<Node>> nodes = NetworkUtils2.getNodesInBoundary(network, regionBoundary);

        // Write headers to output files
        PrintWriter out1;
        log.info("Writing header to file: " + densitiesOutputFile);
        out1 = ioUtils.openFileForSequentialWriting(new File(densitiesOutputFile),false);
        assert out1 != null;
        out1.println("mcAmbience,mcStress,detour,count");
        out1.close();
        log.info("Closing file: " + densitiesOutputFile);

        PrintWriter out2;
        log.info("Writing header to file: " + largeDetoursOutputFile);
        out2 = ioUtils.openFileForSequentialWriting(new File(largeDetoursOutputFile),false);
        assert out2 != null;
        out2.println("mcAmbience,mcStress,originNode,destinationNode,timeFast,timeJibe");
        out2.close();
        log.info("Closing file: " + largeDetoursOutputFile);

        // Loop through possible ambience and stress values
        double intervalAmbience = MAX_MC_AMBIENCE / AMBIENCE_SAMPLES;
        double intervalStress = MAX_MC_STRESS / STRESS_SAMPLES;
        NodeDetourCalculator calc;
        for(int i = 0 ; i <= AMBIENCE_SAMPLES ; i++) {
            double mcAmbience = intervalAmbience * i;
            for(int j = 0 ; j <= STRESS_SAMPLES ; j++) {
                double mcStress = intervalStress * j;
                TravelDisutility td = new JibeDisutility3(mode,tt,true,mcAmbience,mcStress);

                // Run calculation
                calc = new NodeDetourCalculator();
                long[] detourDensities = calc.calculate(network,nodes,tt,td,veh);
                ConcurrentHashMap<String,double[]> largeDetours = calc.getLargeDetourData();
                log.info("Completed calculation for mcAmbience: " + mcAmbience + " mcStress: " + mcStress + ".");

                // Save densities to CSV
                log.info("Appending detour densities to file: " + densitiesOutputFile);
                out1 = ioUtils.openFileForSequentialWriting(new File(densitiesOutputFile),true);
                assert out1 != null;
                for(int k = 0 ; k < 500 ; k++) {
                    double detour = (((double) k) / 100) + 1;
                    long count = detourDensities[k];
                    out1.println(mcAmbience + "," + mcStress + "," + detour + "," + count);
                }
                out1.close();
                log.info("Closing file: " + densitiesOutputFile);

                // Save large detours to CSV
                if(largeDetours.isEmpty()) {
                    log.info("No large detours to print.");
                } else {
                    log.info("Writing " + largeDetours.size() + " OD pairs with large detours to: " + largeDetoursOutputFile);
                    out2 = ioUtils.openFileForSequentialWriting(new File(largeDetoursOutputFile),true);
                    assert out2 != null;
                    for(Map.Entry<String,double[]> e : largeDetours.entrySet()) {
                        out2.println(mcAmbience + "," + mcStress + "," + e.getKey() + "," + e.getValue()[0] + "," + e.getValue()[1]);
                    }
                    out2.close();
                    log.info("Closing file:tdragon " + densitiesOutputFile);
                }
            }
        }
    }
}
