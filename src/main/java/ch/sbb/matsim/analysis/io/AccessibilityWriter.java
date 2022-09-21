/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.io;

import ch.sbb.matsim.analysis.data.AccessibilityData;
import com.google.common.math.LongMath;
import org.apache.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Helper methods to write and read matrices as CSV files (well, actually semi-colon separated files).
 *
 * @author mrieser / SBB
 */
public final class AccessibilityWriter {

    private final static Logger log = Logger.getLogger(AccessibilityWriter.class);

    private final static String SEP = ";";
    private final static String NL = "\n";

    public static<T> void writeAsCsv(AccessibilityData<T> accessibilityData, String filename) throws IOException {
        try (BufferedWriter writer = IOUtils.getBufferedWriter(filename)) {
            int attributeCount = accessibilityData.attrData.size();
            writer.write("ZONE" + SEP + "ACCESSIBILITY");
            for(int i = 0 ; i < attributeCount ; i++) {
                writer.write(SEP + "ATTR_" + i);
            }
            writer.write(NL);

            T[] zoneIds = getSortedIds(accessibilityData.zone2index);
            int counter = 0;
            for (T zoneId : zoneIds) {
                counter++;
                if(LongMath.isPowerOfTwo(counter)) {
                    log.info("Writing zone " + counter + " / " + zoneIds.length);
                }
                writer.write(zoneId.toString());
//                writer.append(SEP);
//                writer.write(Double.toString(accessibilityData.getTime(zoneId)));
                writer.append(SEP);
                writer.write(Double.toString(accessibilityData.getCost(zoneId)));
                for(int i = 0 ; i < attributeCount ; i++) {
                    writer.append(SEP);
                    writer.write(Double.toString(accessibilityData.getAttr(zoneId,i)));
                }
                writer.append(NL);
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
