package trads;

import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.ActiveAttributes;
import routing.Bicycle;
import routing.TravelAttribute;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;
import trads.calculate.RouteIndicatorCalculator;
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trads.io.TradsUniqueRouteWriter;
import trip.Trip;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class RunMultiRouter {

    private final static Logger logger = Logger.getLogger(RunMultiRouter.class);

    // Parameters for MC Simulation
    private final static double MAX_MC = 0.002;
    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 4) {
            throw new RuntimeException("Program requires 4 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output gpkg prefix\n" +
                    "(2) Output csv file path\n" +
                    "(3) Mode");
        }

        Resources.initializeResources(args[0]);
        String outputPrefix = args[1];
        String outputCsv = args[2];
        String mode = args[3];

        // Read network
        Network modeSpecificNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);

        // Filter to only routable trips with chosen mode
        Set<Trip> selectedTrips = trips.stream()
                .filter(t -> t.routable(ORIGIN, DESTINATION) && t.getMainMode().equals(mode))
                .collect(Collectors.toSet());

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

        // CALCULATOR
        RouteIndicatorCalculator calc = new RouteIndicatorCalculator(selectedTrips);

        // JIBE Attributes
        LinkedHashMap<String, TravelAttribute> jibeAttr = ActiveAttributes.getJibe(mode,veh);

        // Run short and fast routing (for reference)
        JibeDisutility tdShort = new JibeDisutility(mode,tt,0.,1.,0.,0.,0.,0.);
        JibeDisutility tdFast = new JibeDisutility(mode,tt,0.0067,0.,0.,0.,0.,0.);

        calc.network("short", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, tdShort, tt, jibeAttr, true);
        calc.network("fast", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, tdFast, tt, jibeAttr, true);
//        calc.network("jibeAmb", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new JibeDisutility(mode,tt, MAX_MC,0.), tt, jibeAttr, true);
//        calc.network("jibeStr", ORIGIN, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new JibeDisutility(mode,tt,0.,MAX_MC), tt, jibeAttr, true);

        // Test different Ambience/stress values
        double mcAmbience;
        double mcStress;
        for (int i = 0; i <= 10; i++) {
            mcAmbience = MAX_MC * i / 10;
            for (int j = 0 ; j <= 10 ; j++) {
                mcStress = MAX_MC * j / 10;
                JibeDisutility disutilty = new JibeDisutility(mode,tt,mcAmbience,mcStress);
                calc.network("jibe_" + i + "_" + j,ORIGIN,DESTINATION,veh,modeSpecificNetwork,modeSpecificNetwork,disutilty,tt,jibeAttr,true);

            }
        }

        // Write results to CSV
        TradsCsvWriter.write(selectedTrips,outputCsv,calc.getAllAttributeNames());

        // Write results to one GPKG
        logger.info("Writing results to gpkg file...");
        TradsUniqueRouteWriter.write(selectedTrips, outputPrefix + ".gpkg");

/*        // Also split into multiple GPKG files
        HashMap<String, Set<Trip>> seperatedTrips = new HashMap<>();
        for(Trip trip : selectedTrips) {
            String od = Stream.of(trip.getZone(ORIGIN),trip.getZone(DESTINATION)).sorted().reduce("_",String::concat);
            seperatedTrips.computeIfAbsent(od,k -> new LinkedHashSet<>()).add(trip);
        }
        int j = 0;
        for(Set<Trip> tripSet : seperatedTrips.values()) {
            j++;
            TradsUniqueRouteWriter.write(tripSet,inputEdgesGpkg,outputPrefix + "_" + j + ".gpkg");
        }
        logger.info("Wrote " + j + " route geopackages.");*/
    }
}
