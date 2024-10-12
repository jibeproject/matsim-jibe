package estimation;

import estimation.specifications.AbstractModelSpecification;
import estimation.specifications.manchester.HBD;
import gis.GisUtils;
import gis.GpkgReader;
import io.DiaryReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.Bicycle;
import routing.travelTime.WalkTravelTime;
import smile.classification.ClassLabels;
import trip.Trip;

import java.io.IOException;
import java.util.*;

public class RunMnlManchester {

    private final static boolean COMPUTE_ROUTE_DATA = true;

    private final static Logger logger = Logger.getLogger(RunMnlManchester.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Input data file \n" +
                    "(2) Output results file");
        }

        Resources.initializeResources(args[0]);

        // Read in TRADS trips from CSV
        logger.info("Reading fixed input data from ascii file...");
        LogitData logitData = new LogitData(args[1],"choice","t.ID");
        logitData.read();

        // Organise classes
        int[] y = logitData.getChoices();
        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        y = codec.y;
        System.out.println("Identified " + k + " classes.");

        // Declare utility specification
        AbstractModelSpecification u;

        if(COMPUTE_ROUTE_DATA) {

            // Read Boundary Shapefile
            logger.info("Reading boundary shapefile...");
            Geometry boundary = GpkgReader.readNetworkBoundary();

            // Read in TRADS trips
            logger.info("Reading person micro data from ascii file...");
            Set<Trip> trips = DiaryReader.readTrips(boundary);

            List<String> ids = List.of(logitData.getIds());
            Trip[] trip_data = new Trip[ids.size()];
            for(Trip trip : trips) {
                String combinedId = trip.getHouseholdId() + trip.getPersonId() + trip.getTripId();
                int i = ids.indexOf(combinedId);
                if(i > -1) {
                    trip_data[i] = trip;
                }
            }

            // check all records are attached to a trip (also ensures none of them are null)
            List<Trip> tripList = List.of(trip_data); // Possibly better to use this in dynamic router instead of array...

            // Read network
            Network network = NetworkUtils2.readFullNetwork();
            Network networkBike = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.bike);
            Network networkWalk = NetworkUtils2.extractModeSpecificNetwork(network, TransportMode.walk);

            // Travel Time
            Bicycle bicycle = new Bicycle(null);
            Vehicle bike = bicycle.getVehicle();
            TravelTime ttWalk = new WalkTravelTime();
            TravelTime ttBike = bicycle.getTravelTimeFast(networkBike);

            // Deal with intrazonal trips – can remove after we get X/Y coordinates for TRADS)
            Set<SimpleFeature> OAs = GisUtils.readGpkg("zones/2011/gm_oa.gpkg");

            // Initialise utility specification
            u = new HBD(logitData,trip_data,OAs,networkBike,bike,ttBike,networkWalk,null,ttWalk);
        } else {
            u = new HBD(logitData,null,null,null,null,null,null,null,null);
        }

        // Start model
        MultinomialLogit.run(u,y,k,0,1e-10,500,args[2]);

        logger.info("finished estimation.");
    }
}
