package trads;

import com.google.common.math.LongMath;
import data.Place;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import org.apache.log4j.Logger;


import java.io.*;
import java.util.*;

import static data.Place.*;

public class TradsIo {

    private final static String HOUSEHOLD_ID = "IDNumber";
    private final static String PERSON_ID = "PersonNumber";
    private final static String TRIP_ID = "TripNumber";

    private final static String START_TIME = "StartTime";

    private final static String SEP = ",";
    private final static String NL = "\n";

    private final static String X_HOUSEHOLD_COORD = "HomeEasting";
    private final static String Y_HOUSEHOLD_COORD = "HomeNorthing";

    private final static String X_MAIN_COORD = "MainEasting";
    private final static String Y_MAIN_COORD = "MainNorthing";

    private final static String X_ORIGIN_COORD = "StartEasting";
    private final static String Y_ORIGIN_COORD = "StartNorthing";

    private final static String X_DESTINATION_COORD = "EndEasting";
    private final static String Y_DESTINATION_COORD = "EndNorthing";

    private final static Logger logger = Logger.getLogger(TradsIo.class);

    // Reads Survey File with Coordinate Data
    static Set<TradsTrip> readTrips(String filePath, Geometry geometry) throws IOException {
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
            int startTime;
            try {
                startTime = Integer.parseInt(lineElements[posStartTime]);
            } catch (NumberFormatException e) {
                startTime = (int) Double.parseDouble(lineElements[posStartTime]);
                logger.warn("Unusual start time " + lineElements[posStartTime] + " for household " + householdId + ", person " + personId + ", trip " + tripId +
                        ". Set to " + startTime);
                badTimes++;
            }

            Map<Place,Coord> coords = new HashMap<>(3);
            Map<Place,Boolean> coordsInBoundary = new HashMap<>(3);

            // Read household coord
            try {
                double x = Double.parseDouble(lineElements[posHomeX]);
                double y = Double.parseDouble(lineElements[posHomeY]);
                coords.put(HOME,CoordUtils.createCoord(x,y));
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
            try {
                double x = Double.parseDouble(lineElements[posOrigX]);
                double y = Double.parseDouble(lineElements[posOrigY]);
                coords.put(ORIGIN, CoordUtils.createCoord(x,y));
                coordsInBoundary.put(ORIGIN, geometry.contains(gf.createPoint(new Coordinate(x,y))));
            } catch (NumberFormatException e) {
                logger.warn("Unreadable ORIGIN coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                badCoords++;
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

            trips.add(new TradsTrip(householdId, personId, tripId, startTime, coords, coordsInBoundary));
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

    // Write results to file
    static void writeIndicators(Set<TradsTrip> trips, String filePath, Map<String, List<String>> attributes) {

        PrintWriter out = openFileForSequentialWriting(new File(filePath));

        out.println(createHeader(attributes));

        int counter = 0;
        for (TradsTrip trip : trips) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " records written.");
            }
            out.println(createRow(trip,attributes));
        }
        out.close();
        logger.info("Wrote " + counter + " records to " + filePath);
    }

    private static String createHeader(Map<String,List<String>> attributes) {
        StringBuilder builder = new StringBuilder();

        // Trip identifiers
        builder.append(HOUSEHOLD_ID).
                append(SEP).append(PERSON_ID).
                append(SEP).append(TRIP_ID).
                append(SEP).append("HomeWithinBoundary").
                append(SEP).append("OriginWithinBoundary").
                append(SEP).append("DestinationWithinBoundary").
                append(SEP).append("SameHomeAndDest").
                append(SEP).append("SameMainAndDest").
                append(SEP).append("SameOrigAndDest");

        // Route attributes
        for (Map.Entry<String, List<String>> e1 : attributes.entrySet()) {
            String route = e1.getKey();
            for (String attributeName : e1.getValue()) {
                builder.append(SEP).append(route);
                if(!attributeName.equals("")) {
                    builder.append("_").append(attributeName);
                }
            }
        }
        return builder.toString();
    }

    private static String createRow(TradsTrip trip, Map<String, List<String>> attributes) {
        StringBuilder builder = new StringBuilder();

        // Trip identifiers
        builder.append(trip.getHouseholdId()).
                append(SEP).append(trip.getPersonId()).
                append(SEP).append(trip.getTripId()).
                append(SEP).append(trip.isWithinBoundary(HOME)).
                append(SEP).append(trip.isWithinBoundary(ORIGIN)).
                append(SEP).append(trip.isWithinBoundary(DESTINATION)).
                append(SEP).append(trip.match(HOME, DESTINATION)).
                append(SEP).append(trip.match(MAIN, DESTINATION)).
                append(SEP).append(trip.match(ORIGIN, DESTINATION));

        // Route attributes
        for (Map.Entry<String, List<String>> e1 : attributes.entrySet()) {
            String route = e1.getKey();
            for(String attributeName : e1.getValue()) {
                builder.append(SEP).append(trip.getAttribute(route, attributeName));
            }
        }
        return builder.toString();
    }

    private static PrintWriter openFileForSequentialWriting(File outputFile) {
        if (outputFile.getParent() != null) {
            File parent = outputFile.getParentFile();
            parent.mkdirs();
        }

        try {
            FileWriter fw = new FileWriter(outputFile, false);
            BufferedWriter bw = new BufferedWriter(fw);
            return new PrintWriter(bw);
        } catch (IOException var5) {
            logger.info("Could not open file <" + outputFile.getName() + ">.");
            return null;
        }
    }
}
