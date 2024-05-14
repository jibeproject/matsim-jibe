/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.io;

import ch.sbb.matsim.analysis.data.IndicatorData;
import com.google.common.math.LongMath;
import org.apache.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helper methods to write and read matrices as CSV files (well, actually semi-colon separated files).
 *
 * @author mrieser / SBB
 */
public final class IndicatorWriter {

    private final static Logger log = Logger.getLogger(IndicatorWriter.class);

    private final static String SEP = ",";
    private final static String NL = "\n";
    private final static String SHORTEST_DISTANCE_ROUTE_NAME = "shortestDistance";
    private final static String LEAST_TIME_ROUTE_NAME = "fastest";

    public static<T> void writeAsCsv(IndicatorData<T> indicatorData, String filename) throws IOException {
        HashMap<String, IndicatorData> multiIndicatorData = new HashMap<>();
        multiIndicatorData.put("NA",indicatorData);
        writeAsCsv(multiIndicatorData, filename);
    }

    public static<T> void writeAsCsv(HashMap<String, IndicatorData> multiIndicatorData, String filename) throws IOException {
        try (BufferedWriter writer = IOUtils.getBufferedWriter(filename)) {

            final Set<String> attributes = multiIndicatorData.entrySet().iterator().next().getValue().attributeMatrices.keySet();

            // If there is a "shortest distance route", sort detour info
            IndicatorData shortestDistanceData = multiIndicatorData.get(SHORTEST_DISTANCE_ROUTE_NAME);
            IndicatorData leastTimeData = multiIndicatorData.get(LEAST_TIME_ROUTE_NAME);

            // Write header
            writer.write(createHeader(attributes));

            for (Map.Entry<String,IndicatorData> entry : multiIndicatorData.entrySet()) {

                log.info("Writing indicators for route " + entry.getKey());

                IndicatorData<T> indicatorData = entry.getValue();

                // Prep
                T[] fromZoneIds = getSortedIds(indicatorData.orig2index);
                T[] toZoneIds = getSortedIds(indicatorData.dest2index);

                // Loop through zone IDs
                int counter = 0;
                for (T fromZoneId : fromZoneIds) {
                    counter++;
                    if (LongMath.isPowerOfTwo(counter)) {
                        log.info("Processing zone " + counter + " / " + fromZoneIds.length);
                    }
                    for (T toZoneId : toZoneIds) {
                        if(indicatorData.linkCountMatrix.get(fromZoneId,toZoneId) > 0) {
                            double distanceM = indicatorData.distanceMatrix.get(fromZoneId,toZoneId);
                            double timeS = indicatorData.travelTimeMatrix.get(fromZoneId,toZoneId);
                            Double distanceDetour = null;
                            if(shortestDistanceData != null) {
                                double shortestDistanceM = shortestDistanceData.distanceMatrix.get(fromZoneId,toZoneId);
                                distanceDetour = distanceM / shortestDistanceM;
                            }
                            Double timeDetour = null;
                            if(leastTimeData != null) {
                                double leastTimeS = leastTimeData.travelTimeMatrix.get(fromZoneId,toZoneId);
                                timeDetour = timeS / leastTimeS;
                            }
                            writer.write(entry.getKey());
                            writer.write(SEP + fromZoneId.toString());
                            writer.write(SEP + toZoneId.toString());
                            writer.write(SEP + indicatorData.costMatrix.get(fromZoneId,toZoneId));
                            writer.write(SEP + indicatorData.linkCountMatrix.get(fromZoneId,toZoneId));
                            writer.write(SEP + distanceM);
                            writer.write(SEP + timeS);
                            writer.write(SEP + 3.6 * distanceM / timeS);
                            writer.write(SEP + distanceDetour);
                            writer.write(SEP + timeDetour);
                            for(String attribute : attributes) {
                                double attr = indicatorData.attributeMatrices.get(attribute).get(fromZoneId,toZoneId);
                                if(!attribute.startsWith("c_")) {
                                    attr /= distanceM;
                                }
                                writer.write(SEP + attr);
                            }
                            writer.write(NL);
                        }
                    }
                }
            }
        }
        log.info("Finished writing.");
    }

    private static String createHeader(Set<String> attributes) {
        StringBuilder builder = new StringBuilder();
        builder.append("Route");
        builder.append(SEP + "From");
        builder.append(SEP + "To");
        builder.append(SEP + "cost");
        builder.append(SEP + "links");
        builder.append(SEP + "distance_m");
        builder.append(SEP + "tt_s");
        builder.append(SEP + "avgSpeed_kph");
        builder.append(SEP + "distance_detour");
        builder.append(SEP + "time_detour");
        for(String attribute : attributes) {
            builder.append(SEP + attribute);
        }
        builder.append(NL);
        return builder.toString();
    }

    private static <T> T[] getSortedIds(Map<T, Integer> id2index) {
        // the array-creation is only safe as long as the generated array is only within this class!
        @SuppressWarnings("unchecked")
        T[] ids = (T[]) (new Object[id2index.size()]);
        for (Map.Entry<T, Integer> e : id2index.entrySet()) {
            ids[e.getValue()] = e.getKey();
        }
        return ids;
    }

    @FunctionalInterface
    public interface IdConverter<T> {
        T parse(String id);
    }
}
