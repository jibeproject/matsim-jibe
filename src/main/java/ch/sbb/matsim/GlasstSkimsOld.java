package ch.sbb.matsim;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.opengis.referencing.FactoryException;

import java.io.IOException;
import java.util.Random;

// Builds skim matrices for the GLASST project (using default classes from MATSim-SBB-Extensions)
public class GlasstSkimsOld {

    private final static Logger log = Logger.getLogger(GlasstSkimsOld.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 8) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Network File Path \n" +
                    "(1) Zones shapefile \n" +
                    "(2) Zone ID attribute name \n" +
                    "(3) Public Transport Network File Path \n" +
                    "(4) Public Transport Schedule File Path \n" +
                    "(5) Output File Path \n" +
                    "(6) Number of Threads \n" +
                    "(7) Random seed \n");
        }

        String networkPath = args[0];
        String zoneShapefile = args[1];
        String zoneIdAttributeName = args[2];
        String ptNetworkPath = args[3];
        String ptSchedulePath = args[4];
        String outputDirectory = args[5];
        int numberOfThreads = Integer.parseInt(args[6]);
        int randomSeed = Integer.parseInt(args[7]);

        // Setup Config
        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
        Random random = new Random(randomSeed);
        CalculateSkimMatrices skims = new CalculateSkimMatrices(outputDirectory,numberOfThreads);
        skims.calculateSamplingPointsPerZoneFromNetwork(networkPath,20,zoneShapefile,zoneIdAttributeName, random);

        // CAR
        skims.calculateAndWriteNetworkMatrices(networkPath,null,new double[] {0},config,"car",
                l -> !((boolean) l.getAttributes().getAttribute("motorway")));

        // PUBLIC TRANSPORT CALCULATIONS
        skims.calculateAndWritePTMatrices(ptNetworkPath,ptSchedulePath,27000,30600,config,"pt",(l,r) -> r.getTransportMode().equals("rail"));
    }

}
