package trads.io;

import com.google.common.math.LongMath;
import data.Place;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import trads.TradsTrip;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static data.Place.*;
import static trads.io.AttributeNames.*;

public class TradsReader {

    private final static Logger logger = Logger.getLogger(TradsReader.class);

    public static Set<TradsTrip> readTrips(String filePath, Geometry geometry) throws IOException {
        Set<TradsTrip> trips = new HashSet<>();
        String recString;
        int counter = 0;
        int badCoords = 0;
        int badTimes = 0;

        // Open Reader
        BufferedReader in = new BufferedReader(new FileReader(filePath));

        GeometryFactory gf = new GeometryFactory();

        // Read Header
        recString = in.readLine();
        String[] header = recString.split(SEP);
        int posHouseholdId = findPositionInArray(HOUSEHOLD_ID, header);
        int posPersonId = findPositionInArray(PERSON_ID, header);
        int posTripId = findPositionInArray(TRIP_ID, header);
        int posStartTime = findPositionInArray(START_TIME, header);
        int posMainMode = findPositionInArray(MAIN_MODE, header);
        int posHomeX = findPositionInArray(X_HOUSEHOLD_COORD, header);
        int posHomeY = findPositionInArray(Y_HOUSEHOLD_COORD, header);
        int posMainX = findPositionInArray(X_MAIN_COORD, header);
        int posMainY = findPositionInArray(Y_MAIN_COORD, header);
        int posOrigX = findPositionInArray(X_ORIGIN_COORD, header);
        int posOrigY = findPositionInArray(Y_ORIGIN_COORD, header);
        int posDestX = findPositionInArray(X_DESTINATION_COORD, header);
        int posDestY = findPositionInArray(Y_DESTINATION_COORD, header);

        while ((recString = in.readLine()) != null) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " records processed.");
            }
            String[] lineElements = recString.split(SEP);

            String householdId = lineElements[posHouseholdId];
            int personId = Integer.parseInt(lineElements[posPersonId]);
            int tripId = Integer.parseInt(lineElements[posTripId]);

            // Read start time if available, otherwise assume 8am
            int startTime;
            if(posStartTime != -1) {
                try {
                    startTime = Integer.parseInt(lineElements[posStartTime]);
                } catch (NumberFormatException e) {
                    startTime = (int) Double.parseDouble(lineElements[posStartTime]);
                    logger.warn("Unusual start time " + lineElements[posStartTime] + " for household " + householdId + ", person " + personId + ", trip " + tripId +
                            ". Set to " + startTime);
                    badTimes++;
                }
            } else startTime = 28800;

            // Read main mode
            String mainMode = lineElements[posMainMode];

            Map<Place, Coord> coords = new HashMap<>(3);
            Map<Place,Boolean> coordsInBoundary = new HashMap<>(3);

            // Read household coord
            try {
                double x = Double.parseDouble(lineElements[posHomeX]);
                double y = Double.parseDouble(lineElements[posHomeY]);
                coords.put(HOME, CoordUtils.createCoord(x,y));
                coordsInBoundary.put(HOME,geometry.contains(gf.createPoint(new Coordinate(x,y))));
            } catch (NumberFormatException e) {
                logger.warn("Unreadable HOME coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                badCoords++;
            }

            // Read main coord (if the main vars are there)
            if(posMainX != -1 && posMainY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posMainX]);
                    double y = Double.parseDouble(lineElements[posMainY]);
                    coords.put(MAIN,CoordUtils.createCoord(x,y));
                    coordsInBoundary.put(MAIN,geometry.contains(gf.createPoint(new Coordinate(x,y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable MAIN coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            // Read origin coord
            if(posOrigX != -1 && posOrigY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posOrigX]);
                    double y = Double.parseDouble(lineElements[posOrigY]);
                    coords.put(ORIGIN, CoordUtils.createCoord(x,y));
                    coordsInBoundary.put(ORIGIN, geometry.contains(gf.createPoint(new Coordinate(x,y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable ORIGIN coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            // Read destination coord
            try {
                double x = Double.parseDouble(lineElements[posDestX]);
                double y = Double.parseDouble(lineElements[posDestY]);
                coords.put(DESTINATION, CoordUtils.createCoord(x,y));
                coordsInBoundary.put(DESTINATION,geometry.contains(gf.createPoint(new Coordinate(x,y))));
            } catch (NumberFormatException e) {
                logger.warn("Unreadable DESTINATION coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                badCoords++;
            }

            trips.add(new TradsTrip(householdId, personId, tripId, startTime, mainMode, coords, coordsInBoundary));
        }
        in.close();

        logger.info(counter + " trips processed.");
        logger.info(badTimes + " trips with double-value start times.");
        logger.info(badCoords + " unreadable coordinates.");

        return trips;
    }

    private static int findPositionInArray (String string, String[] array) {
        int ind = -1;
        for (int a = 0; a < array.length; a++) {
            if (array[a].equalsIgnoreCase(string)) {
                ind = a;
            }
        }
        if (ind == -1) {
            logger.error ("Could not find element " + string +
                    " in array (see method <findPositionInArray> in class <SiloUtil>");
        }
        return ind;
    }
}
