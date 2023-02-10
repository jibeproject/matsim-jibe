package network;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import resources.Properties;
import resources.Resources;

public class CreateMatsimNetworkSingleMode {
    private final static Logger log = Logger.getLogger(CreateMatsimNetworkSingleMode.class);

    public static void main(String[] args) {
        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments:\n" +
                    "(0) Properties file \n" +
                    "(1) Output file name (.xml)\n" +
                    "(2) Mode");
        }

        Resources.initializeResources(args[0]);

        String fullNetworkPath = Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK);
        String singleModeNetworkPath = args[1];
        String mode = args[2];


        // Read MATSim network
        log.info("Reading MATSim network...");
        Network fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(fullNetworkPath);

        // Filter network to a specific mode & clean
        Network singleModeNetwork = NetworkUtils2.extractModeSpecificNetwork(fullNetwork,mode);

        // Write
        new NetworkWriter(singleModeNetwork).write(singleModeNetworkPath);
    }
}
