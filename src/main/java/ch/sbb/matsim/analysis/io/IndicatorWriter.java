/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.io;

import ch.sbb.matsim.analysis.data.IndicatorData;
import com.google.common.math.LongMath;
import org.apache.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;

import java.io.*;
import java.util.Map;

/**
 * Helper methods to write and read matrices as CSV files (well, actually semi-colon separated files).
 *
 * @author mrieser / SBB
 */
public final class IndicatorWriter {

    private final static Logger log = Logger.getLogger(IndicatorWriter.class);

    private final static String SEP = ",";
    private final static String NL = "\n";

    public static<T> void writeAsCsv(IndicatorData<T> networkData, String filename) throws IOException {
        try (BufferedWriter writer = IOUtils.getBufferedWriter(filename)) {
            int attributeCount = networkData.attributeMatrices.size();
            writer.write("FROM" + SEP + "TO" + SEP + "TIME" + SEP + "DISTANCE" + SEP + "LINKS");
            for(int i = 0 ; i < attributeCount ; i++) {
                writer.write(SEP + "ATTR_" + i);
            }
            writer.write(NL);

            T[] fromZoneIds = getSortedIds(networkData.orig2index);
            T[] toZoneIds = getSortedIds(networkData.dest2index);
            int counter = 0;
            for (T fromZoneId : fromZoneIds) {
                counter++;
                if(LongMath.isPowerOfTwo(counter)) {
                    log.info("Writing zone " + counter + " / " + fromZoneIds.length);
                }
                for (T toZoneId : toZoneIds) {
                    writer.write(fromZoneId.toString());
                    writer.append(SEP);
                    writer.write(toZoneId.toString());
                    writer.append(SEP);
                    writer.write(Float.toString(networkData.travelTimeMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(networkData.distanceMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Short.toString(networkData.linkCountMatrix.get(fromZoneId, toZoneId)));
                    for(int i = 0 ; i < attributeCount ; i++) {
                        writer.append(SEP);
                        writer.write(Float.toString(networkData.attributeMatrices.get(i).get(fromZoneId,toZoneId)));
                    }
                    writer.append(NL);
                }
            }
            writer.flush();
        }
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
