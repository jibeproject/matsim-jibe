package io;
import ch.sbb.matsim.analysis.skims.FloatMatrix;
import omx.OmxFile;
import omx.OmxLookup;
import omx.OmxMatrix;
import omx.hdf5.OmxConstants;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class OmxWriter {

    private final static Logger logger = Logger.getLogger(OmxWriter.class);

    public static void createOmxFile(String omxFilePath, int numberOfZones) {

        try (OmxFile omxFile = new OmxFile(omxFilePath)) {
            int[] shape = {numberOfZones, numberOfZones};
            omxFile.openNew(shape);
            omxFile.save();
        }
    }


    public static void createOmxSkimMatrix(String omxFilePath, Map<String, double[][]> matrices, Map<Integer, Integer> id2index) {

        // Create lookup array
        int[] indices = new int[id2index.size()];
        for(Map.Entry<Integer,Integer> e : id2index.entrySet()) {
            indices[e.getValue()] = e.getKey();
        }

        // Write OMX
        writeOmx(omxFilePath, matrices, indices);
    }

    public static void createOmxSkimMatrix(String omxFilePath, Set<String> zoneNames, Map<String, FloatMatrix<String>> matricesToWrite) {
        int zoneCount = zoneNames.size();

        // Create lookup array
        int[] zoneLookup = new int[zoneCount];
        int i = 0;
        for(String zone : zoneNames) {
            zoneLookup[i] = Integer.parseInt(zone);
            i++;
        }
        Arrays.sort(zoneLookup);

        // Convert matrices to double matrix
        Map<String,double[][]> matrices = new LinkedHashMap<>(matricesToWrite.size());
        for(Map.Entry<String, FloatMatrix<String>> e : matricesToWrite.entrySet()) {
            double[][] mat = new double[zoneCount][zoneCount];
            for (int origIdx = 0 ; origIdx < zoneCount ; origIdx++) {
                for (int destIdx = 0 ; destIdx < zoneCount ; destIdx++) {
                    mat[origIdx][destIdx] = e.getValue().get(((Integer) zoneLookup[origIdx]).toString(),((Integer) zoneLookup[destIdx]).toString());
                }
            }
            matrices.put(e.getKey(),mat);
        }

        // Write Omx
        writeOmx(omxFilePath,matrices,zoneLookup);
    }

    private static void writeOmx(String omxFilePath, Map<String, double[][]> matrices, int[] indices) {
        try (OmxFile omxFile = new OmxFile(omxFilePath)) {
            try {
                omxFile.openReadWrite();
            } catch (IllegalArgumentException e) {
                createOmxFile(omxFilePath, indices.length);
                omxFile.openReadWrite();
            }

            OmxLookup<int[], Integer> lookup = new OmxLookup.OmxIntLookup("zone", indices, -1);
            omxFile.addLookup(lookup);

            for (Map.Entry<String,double[][]> e : matrices.entrySet()) {
                String omxMatrixName = e.getKey();
                double[][] array = e.getValue();
                OmxMatrix.OmxDoubleMatrix mat = new OmxMatrix.OmxDoubleMatrix(omxMatrixName, array, -1.);
                mat.setAttribute(OmxConstants.OmxNames.OMX_DATASET_TITLE_KEY.getKey(), "skim_matrix");
                omxFile.addMatrix(mat);
            }
            omxFile.save();
            logger.info(omxFile.summary());
            omxFile.close();
            logger.info(omxFilePath + " matrices written.");
        }
    }
}
