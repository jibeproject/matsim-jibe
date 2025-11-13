package diary;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelTime;
import routing.ActiveAttributes;
import routing.Bicycle;
import routing.TravelAttribute;
import routing.disutility.DistanceDisutility;
import routing.disutility.LaurentDisutility;
import routing.travelTime.WalkTravelTime;
import diary.calculate.RouteIndicatorCalculator;
import io.TripCsvWriter;
import io.DiaryReader;
import io.TripRouteWriter;
import trip.Trip;

import java.io.*;
import java.util.*;

import static trip.Place.*;

public class RunRouter {

    private final static Logger logger = Logger.getLogger(RunRouter.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Route data output (.csv) \n" +
                    "(2) Link data output (specify .gpkg OR write \"true\" to include links in csv file. Otherwise write \"false\") \n");
        }

        Resources.initializeResources(args[0]);
        String outputCsv = args[1];
        String outputGpkg = null;
        boolean savePath = false;

        if(args[2] != null) {
            if (args[2].endsWith(".gpkg")) {
                outputGpkg = args[2];
                savePath = true;
            } else {
                savePath = Boolean.parseBoolean(args[2]);
            }
        }

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = DiaryReader.readTrips(boundary);
        Network network = NetworkUtils2.readFullNetwork();
        Network networkBike = network;

        // CALCULATOR
        RouteIndicatorCalculator calc = new RouteIndicatorCalculator(trips);

        // beeline
        calc.beeline("beeline", ORIGIN, DESTINATION);

        // Travel time based on average speed only
        TravelTime bikeTravelTime = (l,v,person,vehicle) -> l.getLength() / 4.93;
        TravelTime eBikeTravelTime = (l,v,person,vehicle) -> l.getLength() / 6.04;

        LaurentDisutility bikeDisutility = new LaurentDisutility(network,4.93,0.9349533,false);
        LaurentDisutility eBikeDisutility = new LaurentDisutility(network,6.04,0.9349533,true);

        // bike
//        calc.network("short", ORIGIN, DESTINATION,  null, networkBike, networkBike, new DistanceDisutility(), (l,v,person,vehicle) -> 0.,null,savePath);
        calc.network("bike", ORIGIN, DESTINATION, null, networkBike, networkBike, bikeDisutility, bikeTravelTime, bikeDisutility.getTravelAttributes(),savePath);
        calc.network("ebike", ORIGIN, DESTINATION, null, networkBike, networkBike, eBikeDisutility, eBikeTravelTime, eBikeDisutility.getTravelAttributes(),savePath);


        // Write results
        if (outputCsv != null) {
            logger.info("Writing results to csv file...");
            TripCsvWriter.write(trips, outputCsv, calc.getAllAttributeNames());
        }

        if (outputGpkg != null) {
            logger.info("Writing results to gpkg file...");
            TripRouteWriter.write(trips,network,outputGpkg,false,calc.getAllAttributeNames());
        }
    }
}
