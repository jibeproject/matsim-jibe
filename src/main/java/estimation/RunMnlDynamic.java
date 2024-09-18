package estimation;

import estimation.utilities.AbstractUtilitySpecification;
import estimation.utilities.MNL_Dynamic;
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

public class RunMnlDynamic {

    private final static Logger logger = Logger.getLogger(RunMnlDynamic.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Logit data file");
        }

        Resources.initializeResources(args[0]);

        // Read in TRADS trips from CSV
        logger.info("Reading fixed input data from ascii file...");
        LogitData logitData = new LogitData(args[1],"choice","t.ID");
        logitData.read();

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

        // Organise classes
        int[] y = logitData.getChoices();
        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        y = codec.y;
        System.out.println("Identified " + k + " classes.");

        // Utility function
        AbstractUtilitySpecification u = new MNL_Dynamic(logitData,trip_data,OAs,networkBike,bike,ttBike,networkWalk,null,ttWalk);
//        AbstractUtilityFunction u = new MNL_Static(logitData);


        // Start model
        MultinomialLogit.run(u,y,k,0,1e-10,500,"dynamic6.csv");

        logger.info("finished estimation.");
    }
}
