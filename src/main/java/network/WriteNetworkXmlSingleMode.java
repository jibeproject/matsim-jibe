package network;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.io.NetworkWriter;
import resources.Resources;

public class WriteNetworkXmlSingleMode {

    public static void main(String[] args) {
        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments:\n" +
                    "(0) Properties file \n" +
                    "(1) Output file name (.xml)\n" +
                    "(2) Mode");
        }

        Resources.initializeResources(args[0]);
        String outputPath = args[1];
        String mode = args[2];

        // Read & filter
        Network singleModeNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

        // Write
        new NetworkWriter(singleModeNetwork).write(outputPath);
    }
}
