package accessibility;

import accessibility.decay.DecayFunctions;
import gis.GisUtils;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.opengis.referencing.FactoryException;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;

import java.io.IOException;

public class RunGridAnalysis {
    private final static Logger log = Logger.getLogger(RunGridAnalysis.class);

    final static double SIDE_LENGTH_METERS = 100.; // todo: store this in the grid gpkg somehow

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Network file path \n" +
                    "(1) Accesssibility results file \n" +
                    "(2) Grid file path \n" +
                    "(4) Output file path (.gpkg) \n" +
                    "(5) Number of threads \n");
        }

        String networkPath = args[0];
        String accessibilityResultsFile = args[1];
        String gridFile = args[2];
        String outputFilePath = args[3];
        int numberOfThreads = Integer.parseInt(args[4]);

        // Read network
        log.info("Reading MATSim network...");
        Network fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(networkPath);

        // Read node accessibility results
        log.info("Reading accessibility results...");
        IdMap<Node,Double> nodeAccessibilities = NodeReader.read(accessibilityResultsFile);

        // Create network for extracting nearest links
        Network network = NetworkUtils2.extractModeSpecificNetwork(fullNetwork,TransportMode.walk);
        NetworkUtils2.extractFromNodes(network,nodeAccessibilities.keySet());

        // Creating grid
        log.info("Reading grid...");
        SimpleFeatureCollection grid  = GpkgReader.readGridAndUpdateFeatureType(gridFile);

        // Calculate grid accessibilities
        GridCalculator.calculate(nodeAccessibilities,grid,network, DecayFunctions.WALK_DIST,
                new JibeDisutility(TransportMode.walk, new WalkTravelTime()),null,numberOfThreads);

        // Write grid to gpkg
        GisUtils.writeFeaturesToGpkg(grid,outputFilePath);
    }

}
