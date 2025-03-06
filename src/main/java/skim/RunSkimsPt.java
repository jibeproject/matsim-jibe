package skim;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.analysis.skims.PTSkimMatrices;
import io.OmxWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class RunSkimsPt {

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 3) {
            throw new RuntimeException("""
                    Program requires at least 3 arguments:\s
                    (0) Properties file\s
                    (1) Zone centroids (.csv)\s
                    (2) Output file path (.omx)""");
        }

        Resources.initializeResources(args[0]);
        String zonesFilename = args[1];
        String outputFile = args[2];

        // Load public transport data
        Config config = ConfigUtils.createConfig();
        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        String transitNetworkFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_NETWORK);
        String transitScheduleFilePath = Resources.instance.getString(Properties.MATSIM_TRANSIT_SCHEDULE);

        CalculateSkimMatrices skims = new CalculateSkimMatrices("/",numberOfThreads);
        skims.loadSamplingPointsFromFile(zonesFilename);
        PTSkimMatrices.PtIndicators<String> ptIndicators = skims.calculatePTMatrices(transitNetworkFilePath,transitScheduleFilePath,28800,30600,config,(a,b) -> b.getTransportMode().equals("bus"));

        // Choose which matrices to write
        Map<String, FloatMatrix<String>> matricesToWrite = new LinkedHashMap<>();
        matricesToWrite.put("travelTime",ptIndicators.travelTimeMatrix);
        matricesToWrite.put("accessTime",ptIndicators.accessTimeMatrix);
        matricesToWrite.put("egressTime",ptIndicators.egressTimeMatrix);
        matricesToWrite.put("busTimeShare",ptIndicators.trainTravelTimeShareMatrix);

        // Write matrices
        OmxWriter.createOmxSkimMatrix(outputFile,skims.getCoordsPerZone().keySet(),matricesToWrite);

    }
}
