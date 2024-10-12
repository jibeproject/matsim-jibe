package estimation.dynamic;

import estimation.RouteAttribute;

import java.io.*;
import java.util.*;

public class RouteDataPrecomputed implements RouteData {

    final double[] time;
    final double[][] attributeData;

    public RouteDataPrecomputed(String[] ids, List<RouteAttribute> attributes, String mode) {

        // Initialise data
        time = new double[ids.length];
        attributeData = new double[ids.length][attributes.size()];

        // Read and fill static data
        try {
            RouteDataIO.readAndFillStaticData(ids, attributes, time, attributeData, mode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public double getTime(int i) {
        return time[i];
    }

    public double getAttribute(int i, int j) {
        return attributeData[i][j];
    }

}
