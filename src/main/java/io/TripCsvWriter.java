package io;

import org.apache.log4j.Logger;
import org.matsim.core.utils.misc.Counter;
import resources.Resources;
import resources.Properties;
import trip.Trip;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static trip.Place.*;

public class TripCsvWriter {

    private final static Logger logger = Logger.getLogger(TripCsvWriter.class);
    private final static String SEP = ",";
    private static final String NL = "\n";

    // Write results to csv file
    public static void write(Set<Trip> trips, String filePath, Map<String, List<String>> attributes) {

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(filePath),false);
        assert out != null;

        // Route attributes
        Set<String> allAttributes = new LinkedHashSet<>();
        for(List<String> stringList : attributes.values()) {
            allAttributes.addAll(stringList);
        }

        // Create header
        out.println(createHeader(allAttributes));

        Counter counter = new Counter(""," records written.");
        for (Trip trip : trips) {
            out.print(createRow(trip,attributes.keySet(),allAttributes));
            counter.incCounter();
        }
        out.close();
        logger.info("Wrote " + counter.getCounter() + " trips to " + filePath);
    }

    private static String createHeader(Set<String> allAttributes) {
        StringBuilder builder = new StringBuilder();

        // Trip identifiers
        builder.append(Resources.instance.getString(Properties.HOUSEHOLD_ID)).
                append(SEP).append(Resources.instance.getString(Properties.PERSON_ID)).
                append(SEP).append(Resources.instance.getString(Properties.TRIP_ID)).
                append(SEP).append("Mode").
//              append(SEP).append("HomeWithinBoundary").
                append(SEP).append("OriginWithinBoundary").
                append(SEP).append("DestinationWithinBoundary").
//              append(SEP).append("SameHomeAndDest").
                append(SEP).append("SameOrigAndDest").
//              append(SEP).append("SameMainAndDest").
//              append(SEP).append(Resources.instance.getString(Properties.ORIGIN_ZONE)).
//              append(SEP).append(Resources.instance.getString(Properties.DESTINATION_ZONE)).
                append(SEP).append("Route").
                append(SEP).append("PathId").
                append(SEP).append("LinkIds");

        // Route attributes
        for(String attribute : allAttributes) {
            builder.append(SEP).append(attribute);
        }

        return builder.toString();
    }

    private static String createRow(Trip trip, Set<String> routeNames, Set<String> allAttributes) {
        StringBuilder builder = new StringBuilder();

        for(String routeName : routeNames) {
            // Trip identifiers
            builder.append(trip.getHouseholdId()).
                    append(SEP).append(trip.getPersonId()).
                    append(SEP).append(trip.getTripId()).
                    append(SEP).append(trip.getMainMode()).
//                    append(SEP).append(trip.isWithinBoundary(HOME)).
                    append(SEP).append(trip.isWithinBoundary(ORIGIN)).
                    append(SEP).append(trip.isWithinBoundary(DESTINATION)).
//                    append(SEP).append(trip.match(HOME, DESTINATION)).
                    append(SEP).append(trip.match(ORIGIN, DESTINATION)).
//                    append(SEP).append(trip.match(MAIN, DESTINATION)).
//                    append(SEP).append(trip.getZone(ORIGIN)).
//                    append(SEP).append(trip.getZone(DESTINATION)).
                    append(SEP).append(routeName);

            Integer pathIndex = trip.getPathIndex(routeName);
            if(pathIndex != null) {
                builder.append(SEP).append(pathIndex).
                        append(SEP).append(trip.getUniqueRoutes().get(pathIndex).getLinkIds().stream().map(Object::toString).collect(Collectors.joining("-")));
            } else {
                builder.append(SEP).append(SEP);
            }

            // Attributes
            for(String attribute : allAttributes) {
                builder.append(SEP).append(trip.getAttribute(routeName, attribute));
            }

            // New line
            builder.append(NL);
        }

        return builder.toString();
    }
}
