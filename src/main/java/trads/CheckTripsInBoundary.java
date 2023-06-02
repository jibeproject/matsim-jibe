package trads;

import gis.GpkgReader;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import resources.Resources;
import trads.io.TradsReader;
import trip.Purpose;
import trip.Trip;

import java.io.IOException;
import java.util.Set;

import static trip.Place.*;

public class CheckTripsInBoundary {

    private final static Logger logger = Logger.getLogger(CheckTripsInBoundary.class);

    public static void main(String[] args) throws IOException {

        if(args.length != 1) {
            throw new RuntimeException("Program requires 1 arguments: \n" +
                    "(0) Properties file");
        }
        Resources.initializeResources(args[0]);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);

        logger.info("Total trips: " + trips.size());
        logger.info("Trips in network boundary: " + trips.stream().filter(t -> t.routable(ORIGIN,DESTINATION)).count());
        logger.info("Walk trips: " + trips.stream().filter(t -> t.routable(ORIGIN,DESTINATION) && t.getMainMode().equals(TransportMode.walk)).count());
        logger.info("Bike trips: " + trips.stream().filter(t -> t.routable(ORIGIN,DESTINATION) && t.getMainMode().equals(TransportMode.bike)).count());

        logger.info("MANDATORY Walk trips: " + trips.stream()
                .filter(t -> t.routable(ORIGIN,DESTINATION) &&
                                t.getMainMode().equals(TransportMode.bike) &&
                        ((t.getStartPurpose().equals(Purpose.HOME) && t.getEndPurpose().isMandatory())))
                .count());

        logger.info("DISCRETIONARY Walk trips: " + trips.stream()
                .filter(t -> t.routable(ORIGIN,DESTINATION) &&
                        t.getMainMode().equals(TransportMode.bike) &&
                        t.getStartPurpose().equals(Purpose.HOME) &&
                        !t.getEndPurpose().isMandatory())
                .count());



    }

}
