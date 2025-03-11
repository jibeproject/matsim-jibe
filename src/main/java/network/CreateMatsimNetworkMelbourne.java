package network;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.io.NetworkWriter;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.IOException;

public class CreateMatsimNetworkMelbourne {

    public static void main(String[] args) throws FactoryException, IOException {
        if(args.length > 2) {
            throw new RuntimeException("Program requires 2 arguments:\n" +
                    "(0) Properties file \n" +
                    "(1) Include previous simulation details in network? (true/false)");
        }

        Resources.initializeResources(args[0]);
        Network net = NetworkGpkgToMatsim.convertNetwork(Boolean.parseBoolean(args[1]),null);

        // MELBOURNE-SPECIFIC CALIBRATION ADJUSTMENTS
        for (Link link : net.getLinks().values()) {

            // Double capacity for links under 100 meters
            if(link.getLength() < 100.) {
                link.setCapacity(link.getCapacity() * 2);
            }

        }

        // Write network
        new NetworkWriter(net).write(Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK));
    }
}
