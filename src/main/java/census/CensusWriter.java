package census;

import com.google.common.math.LongMath;
import org.apache.log4j.Logger;
import trip.Trip;

import java.io.*;
import java.util.*;

import static trip.Place.*;

public class CensusWriter {

    private final static Logger logger = Logger.getLogger(CensusWriter.class);
    private final static String SEP = ",";
    private static final String NL = "\n";

    // Write results to csv file
    public static void write(Set<Trip> trips, String filePath, Map<String, List<String>> attributes) {

        PrintWriter out = openFileForSequentialWriting(new File(filePath));
        assert out != null;

        // Route names
        Set<String> routeNames = attributes.keySet();

        // Route attributes
        Set<String> allAttributes = new LinkedHashSet<>();
        for(List<String> stringList : attributes.values()) {
            allAttributes.addAll(stringList);
        }

        out.println(createHeader(allAttributes));

        int counter = 0;
        for (Trip trip : trips) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " records written.");
            }
            out.print(createRow(trip,routeNames,allAttributes));
        }
        out.close();
        logger.info("Wrote " + counter + " records to " + filePath);
    }

    private static String createHeader(Set<String> allAttributes) {
        StringBuilder builder = new StringBuilder();

        // Trip identifiers
        builder.append("Id").
                append(SEP).append("HomeZone").
                append(SEP).append("HomeX").
                append(SEP).append("HomeY").
                append(SEP).append("WorkZone").
                append(SEP).append("WorkX").
                append(SEP).append("WorkY").
                append(SEP).append("Route");

        for(String attribute : allAttributes) {
            builder.append(SEP).append(attribute);
        }

        return builder.toString();
    }

    private static String createRow(Trip trip, Set<String> routes, Set<String> allAttributes) {

        StringBuilder builder = new StringBuilder();

        for(String route : routes) {

            // Trip identifiers
            builder.append(trip.getPersonId()).
                    append(SEP).append(trip.getZone(HOME)).
                    append(SEP).append(trip.getCoord(HOME).getX()).
                    append(SEP).append(trip.getCoord(HOME).getY()).
                    append(SEP).append(trip.getZone(DESTINATION)).
                    append(SEP).append(trip.getCoord(DESTINATION).getX()).
                    append(SEP).append(trip.getCoord(DESTINATION).getY()).
                    append(SEP).append(route);

            // Attributes
            for(String attribute : allAttributes) {
                builder.append(SEP).append(trip.getAttribute(route, attribute));
            }

            // New line
            builder.append(NL);
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
