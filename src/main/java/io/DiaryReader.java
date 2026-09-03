package io;

import trip.Place;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import resources.Properties;
import resources.Resources;
import trip.Trip;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class DiaryReader {

    private final static Logger logger = Logger.getLogger(DiaryReader.class);

    public static Set<Trip> readTrips(Geometry geometry) throws IOException {
        return readTrips(geometry,null);
    }

    public static Set<Trip> readTrips(Geometry geometry, ReadFilter tester) throws IOException {
        Set<Trip> trips = new LinkedHashSet<>();
        String recString;
        Counter counter = new Counter("Processed " + "travel diary records.");
        int badCoords = 0;

        // Open Reader
        String filePath = Resources.instance.getString(Properties.DIARY_FILE);
        if(filePath == null) {
            throw new RuntimeException("No TRADS survey path in the properties file!");
        }
        BufferedReader in = new BufferedReader(new FileReader(filePath));

        GeometryFactory gf = new GeometryFactory();

        // Read Header
        recString = in.readLine();
        String[] header = recString.split(Resources.instance.getString(Properties.DIARY_DELIMITER));
        int posHouseholdId = findPositionInArray(Properties.HOUSEHOLD_ID, header);
        int posPersonId = findPositionInArray(Properties.PERSON_ID, header);
        int posTripId = findPositionInArray(Properties.TRIP_ID, header);
        int posOriginZone = findPositionInArray(Properties.ORIGIN_ZONE,header);
        int posDestinationZone = findPositionInArray(Properties.DESTINATION_ZONE,header);
        int posOrigX = findPositionInArray(Properties.ORIGIN_X, header);
        int posOrigY = findPositionInArray(Properties.ORIGIN_Y, header);
        int posDestX = findPositionInArray(Properties.DESTINATION_X, header);
        int posDestY = findPositionInArray(Properties.DESTINATION_Y, header);

        String sep = Resources.instance.getString(Properties.DIARY_DELIMITER);
        while ((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] lineElements = recString.split(sep);

            String householdId = lineElements[posHouseholdId];
            int personId = Integer.parseInt(lineElements[posPersonId]);
            int tripId = Integer.parseInt(lineElements[posTripId]);

            if(tester != null) {
                if(!tester.test(householdId,personId,tripId)) {
                    continue;
                }
            }

            // ZONES
            Map<Place,String> zones = new HashMap<>(3);
            if(posOriginZone != -1) zones.put(Place.ORIGIN,lineElements[posOriginZone]);
            if(posDestinationZone != -1) zones.put(Place.DESTINATION,lineElements[posDestinationZone]);

            // COORDS
            Map<Place, Coord> coords = new HashMap<>(3);
            Map<Place, Boolean> coordsInBoundary = new HashMap<>(3);

            // Read origin coord
            if(posOrigX != -1 && posOrigY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posOrigX]);
                    double y = Double.parseDouble(lineElements[posOrigY]);
                    coords.put(Place.ORIGIN, CoordUtils.createCoord(x,y));
                    coordsInBoundary.put(Place.ORIGIN, geometry.contains(gf.createPoint(new Coordinate(x,y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable ORIGIN coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            // Read destination coord
            if(posDestX != -1 && posDestY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posDestX]);
                    double y = Double.parseDouble(lineElements[posDestY]);
                    coords.put(Place.DESTINATION, CoordUtils.createCoord(x, y));
                    coordsInBoundary.put(Place.DESTINATION, geometry.contains(gf.createPoint(new Coordinate(x, y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable DESTINATION coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            trips.add(new Trip(householdId, personId, tripId, zones, coords, coordsInBoundary));
        }
        in.close();

        logger.info(counter.getCounter() + " trips processed.");
        logger.info(badCoords + " unreadable coordinates.");

        return trips;
    }

    public interface ReadFilter {
        boolean test(String hhid, int persid, int tripid);

    }

    public interface IdMatcher {
        String combine(String hhid, int persid, int tripid);
    }


    private static int findPositionInArray (String property, String[] array) {
        String string = Resources.instance.getString(property);
        if (string == null) {
            logger.warn("No diary attribute for \"" + property + "\" specified in properties file");
            return -1;
        } else {
            int ind = -1;
            for (int a = 0; a < array.length; a++) {
                if (array[a].equalsIgnoreCase(string)) {
                    ind = a;
                }
            }
            if (ind == -1) {
                logger.error ("Could not find element " + string +
                        " in array");
            }
            return ind;
        }
    }
}
