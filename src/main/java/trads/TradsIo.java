package trads;

import ch.sbb.matsim.analysis.TravelAttribute;
import com.google.common.math.LongMath;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import org.apache.log4j.Logger;


import java.io.*;
import java.util.*;

public class TradsIo {

    private final static String HOUSEHOLD_ID = "IDNumber";
    private final static String PERSON_ID = "PersonNumber";
    private final static String TRIP_ID = "TripNumber";

    private final static String START_TIME = "StartTime";

    private final static String SEP = ",";
    private final static String NL = "\n";


    private final static String X_ORIGIN_COORD = "StartEasting";
    private final static String Y_ORIGIN_COORD = "StartNorthing";

    private final static String X_DESTINATION_COORD = "EndEasting";
    private final static String Y_DESTINATION_COORD = "EndNorthing";

    private final static Logger logger = Logger.getLogger(TradsIo.class);

    // Reads Survey File with Coordinate Data
    static Set<TradsTrip> readTrips(String filePath, Geometry geometry) throws IOException {
        Set<TradsTrip> trips = new HashSet<>();
        String recString = "";
        int counter = 0;
        int badCoords = 0;
        int badTimes = 0;
        int originsOutsideBoundary = 0;
        int destinationsOutsideBoundary = 0;

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

            Coord cOrig = null;
            Coord cDest = null;
            Boolean originInBoundary = null;
            Boolean destinationInBoundary = null;
            Boolean sameOriginDestinationLocation = null;
            try {
                double xOrig = Double.parseDouble(lineElements[posOrigX]);
                double yOrig = Double.parseDouble(lineElements[posOrigY]);
                double xDest = Double.parseDouble(lineElements[posDestX]);
                double yDest = Double.parseDouble(lineElements[posDestY]);

                cOrig = CoordUtils.createCoord(xOrig, yOrig);
                cDest = CoordUtils.createCoord(xDest, yDest);

                sameOriginDestinationLocation = xOrig == xDest && yOrig == yDest;

                originInBoundary = geometry.contains(gf.createPoint(new Coordinate(xOrig, yOrig)));
                destinationInBoundary = geometry.contains(gf.createPoint(new Coordinate(xDest, yDest)));

                if (!originInBoundary) originsOutsideBoundary++;
                if (!destinationInBoundary) destinationsOutsideBoundary++;

            } catch (NumberFormatException e) {
                logger.warn("Unreadable coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                badCoords++;
            }

            trips.add(new TradsTrip(householdId, personId, tripId, startTime, cOrig, cDest,
                    originInBoundary, destinationInBoundary, sameOriginDestinationLocation));
        }
        in.close();
        logger.info(counter + " records processed.");
        logger.info(trips.size() + " trips read successfuly.");
        logger.info(originsOutsideBoundary + " trips with origin coordinates outside study area boundary.");
        logger.info(destinationsOutsideBoundary + " trips with destination coordinates outside study area boundary.");
        logger.info(badCoords + " trips with no or unreadable coordinates.");
        logger.info(badTimes + " trips with double-value start times.");

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
    static void writeIndicators(Set<TradsTrip> trips, String filePath, Map<String, LinkedHashMap<String, TravelAttribute>> nonPtAttributes, List<String> ptAttributes) {

        PrintWriter out = openFileForSequentialWriting(new File(filePath));

        out.println(createHeader(nonPtAttributes,ptAttributes));

        int counter = 0;
        for (TradsTrip trip : trips) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " records written.");
            }
            out.println(createRow(trip,nonPtAttributes,ptAttributes));
        }
        out.close();
        logger.info("Wrote " + counter + " records to " + filePath);
    }

    private static String createHeader(Map<String,LinkedHashMap<String,TravelAttribute>> nonPtAttributes, List<String> ptAttributes) {
        StringBuilder builder = new StringBuilder();

        // Trip identifiers
        builder.append(HOUSEHOLD_ID + SEP + PERSON_ID + SEP + TRIP_ID +
                SEP + "OriginWithinBoundary" + SEP + "DestinationWithinBoundary" + SEP + "SameOrigAndDest");

        // non-PT attributes
        for (Map.Entry<String, LinkedHashMap<String,TravelAttribute>> e1 : nonPtAttributes.entrySet()) {
                String mode = e1.getKey();
                builder.append(SEP + mode + "_cost");
                builder.append(SEP + mode + "_time");
                builder.append(SEP + mode + "_dist");
            if(e1.getValue() != null) {
                for (String attributeName : e1.getValue().keySet()) {
                    builder.append(SEP + mode + "_" + attributeName);
                }
            }
        }

        // PT attributes
        if(ptAttributes != null) {
            for(String attr : ptAttributes) {
                builder.append(SEP + "pt_" + attr);
            }
        }

        return builder.toString();
    }

    private static String createRow(TradsTrip trip, Map<String, LinkedHashMap<String, TravelAttribute>> nonPtAttributes, List<String> ptAttributes) {
        StringBuilder builder = new StringBuilder();

        // Trip identifiers
        builder.append(trip.getHouseholdId() + SEP + trip.getPersonId() + SEP + trip.getTripId() + SEP +
                trip.isOriginWithinBoundary() + SEP + trip.isDestinationWithinBoundary() + SEP + trip.originMatchesDestination());

        // non-PT attributes
        for (Map.Entry<String, LinkedHashMap<String,TravelAttribute>> e1 : nonPtAttributes.entrySet()) {
            String mode = e1.getKey();
            builder.append(SEP + trip.getCost(mode));
            builder.append(SEP + trip.getTime(mode));
            builder.append(SEP + trip.getDistance(mode));
            if(e1.getValue() != null) {
                for(String attributeName : e1.getValue().keySet()) {
                    builder.append(SEP + trip.getAttribute(mode,attributeName));
                }
            }
        }

        // PT attributes
        if (ptAttributes != null) {
            for(String attributeName : ptAttributes) {
                builder.append(SEP + trip.getAttribute("pt",attributeName));
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
