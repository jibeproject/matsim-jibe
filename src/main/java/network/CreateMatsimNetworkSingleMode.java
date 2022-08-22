package network;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;

import java.util.Collections;

public class CreateMatsimNetworkSingleMode {
    private final static Logger log = Logger.getLogger(CreateMatsimNetworkSingleMode.class);

    public static void main(String[] args) {
        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments:\n" +
                    "(0) MATSim network file (.xml)\n" +
                    "(1) MATSim output file (.xml)\n" +
                    "(2) Mode");
        }

        String fullNetworkPath = args[0];
        String singleModeNetworkPath = args[1];
        String mode = args[2];


        // Read MATSim network
        log.info("Reading MATSim network...");
        Network fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(fullNetworkPath);

        // Filter network to a specific mode
        Network singleModeNetwork = filterAndClean(fullNetwork,mode);

        // Write
        new NetworkWriter(singleModeNetwork).write(singleModeNetworkPath);
    }

    public static Network filterAndClean(Network fullNetwork, String mode) {
        Network modeSpecificNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(fullNetwork).filter(modeSpecificNetwork, Collections.singleton(mode));
        NetworkUtils.runNetworkCleaner(modeSpecificNetwork);
        return modeSpecificNetwork;
    }

}
