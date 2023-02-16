package trads.io;

import com.google.common.math.LongMath;
import org.apache.log4j.Logger;
import trip.Trip;

import java.io.*;
import java.util.*;

import static trip.Place.*;
import static trads.io.TradsAttributes.*;

public class TradsWriter {

    private final static Logger logger = Logger.getLogger(TradsWriter.class);

    // Write results to csv file
    public static void write(Set<Trip> trips, String filePath, Map<String, List<String>> attributes) {

        PrintWriter out = openFileForSequentialWriting(new File(filePath));

        out.println(createHeader(attributes));

        int counter = 0;
        for (Trip trip : trips) {
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

    private static String createRow(Trip trip, Map<String, List<String>> attributes) {
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
