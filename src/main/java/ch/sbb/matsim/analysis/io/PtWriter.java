package ch.sbb.matsim.analysis.io;

import ch.sbb.matsim.analysis.data.PtData;
import com.google.common.math.LongMath;
import org.apache.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

public class PtWriter {

    private final static Logger log = Logger.getLogger(PtWriter.class);

    private final static String SEP = ",";
    private final static String NL = "\n";

    public static<T> void writeAsCsv(PtData<T> ptData, String filename) throws IOException {
        try (BufferedWriter writer = IOUtils.getBufferedWriter(filename)) {
            writer.write("FROM" + SEP + "TO" + SEP + "RouteCount" + SEP + "AdaptionTime" + SEP + "Frequency" +
                    SEP + "Distance" + SEP + "TravelTime" + SEP + "AccessTime" + SEP + "EgressTime" + SEP +
                    "TransferCount" + SEP + "TrainTravelTimeShare" + SEP + "TrainDistance");
            writer.write(NL);

            T[] fromZoneIds = getSortedIds(ptData.orig2index);
            T[] toZoneIds = getSortedIds(ptData.dest2index);
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
                    writer.write(Float.toString(ptData.dataCountMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.adaptionTimeMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.frequencyMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.distanceMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.travelTimeMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.accessTimeMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.egressTimeMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.transferCountMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.trainTravelTimeShareMatrix.get(fromZoneId, toZoneId)));
                    writer.append(SEP);
                    writer.write(Float.toString(ptData.trainDistanceShareMatrix.get(fromZoneId, toZoneId)));
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
