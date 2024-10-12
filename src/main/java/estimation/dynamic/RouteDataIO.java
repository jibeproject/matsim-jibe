package estimation.dynamic;

import estimation.RouteAttribute;
import io.ioUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.List;

public class RouteDataIO {

    private static final Logger logger = Logger.getLogger(RouteDataIO.class);

    private static final String ROUTE_DATA_FILE_PREFIX = "estimation/StaticRouteData_";
    private static final String SEP = ",";
    private static final String ID = "ID";  //todo: make these forbidden attribute names?
    private static final String TIME_SUFFIX = "_time";

    public static void writeStaticData(String[] ids, List<RouteAttribute> attributes, RouteDataDynamic dynamic, String mode) {

        String filePath = ROUTE_DATA_FILE_PREFIX + mode + ".csv";

        logger.info("Writing trip attributes to " + filePath + "...");

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(filePath),false);
        assert out != null;

        // HEADER
        StringBuilder header = new StringBuilder();
        header.append(ID).append(SEP).append(mode).append(TIME_SUFFIX);
        for(RouteAttribute attr : attributes) {
            header.append(SEP).append(attr.getName());
        }
        out.println(header);

        // MAIN BODY
        for(int i = 0 ; i < ids.length ; i++) {
            StringBuilder line = new StringBuilder();
            line.append(ids[i]).append(SEP).append(dynamic.getTime(i));
            for(int j = 0 ; j < attributes.size() ; j++) {
                line.append(SEP).append(dynamic.getAttribute(i,j));
            }
            out.println(line);
        }
        out.close();
    }

    public static void readAndFillStaticData(String[] ids, List<RouteAttribute> attributes, double[] time,
                                             double[][] attributeData, String mode) throws IOException {

        String filePath = ROUTE_DATA_FILE_PREFIX + mode + ".csv";

        logger.info("Reading static trip attributes from " + filePath + "...");

        String recString;
        int[] posAttributes = new int[attributes.size()];

        logger.info("Reading pre-loaded data...");
        BufferedReader in = new BufferedReader(new FileReader(filePath));

        // Header
        recString = in.readLine();
        String[] header = recString.split(SEP);
        int posId = findPositionInArray(ID,header);
        int posTime = findPositionInArray(mode + TIME_SUFFIX,header);
        for(int i = 0 ; i < attributes.size() ; i++) {
            posAttributes[i] = findPositionInArray(attributes.get(i).getName(),header);
        }

        // Data
        int i = 0;
        while ((recString = in.readLine()) != null) {
            String[] lineElements = recString.split(",");

            // Check Trip ID matches
            String id = lineElements[posId];
            if(!ids[i].equals(id)) {
                throw new RuntimeException("Record ID (" + ids[i] + ") at line " + i + " doesn't match most recently printed attribute data record ID (" + id + ")");
            }

            // Time
            time[i] = Double.parseDouble(lineElements[posTime]);

            // Attribute data
            for(int j = 0 ; j < attributes.size() ; j++) {
                attributeData[i][j] = Double.parseDouble(lineElements[posAttributes[j]]);
            }

            // Next line
            i++;
        }
    }

    private static int findPositionInArray (String attributeName, String[] array) {
        int ind = -1;
        for (int a = 0; a < array.length; a++) {
            if (array[a].equalsIgnoreCase(attributeName)) {
                ind = a;
            }
        }
        if (ind == -1) {
            throw new RuntimeException("Could not find attribute \"" + attributeName + "\" in most recent route data printout.");
        }
        return ind;
    }

}
