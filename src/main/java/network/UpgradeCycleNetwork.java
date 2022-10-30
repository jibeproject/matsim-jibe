package network;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Java code to upgrade network to the "best" cycle network with the highest level of separation and crossing
// infrastructure. Requires the current network and CSV file of EDGE IDs to modify (each edge on a new line)

public class UpgradeCycleNetwork {

    private final static Logger log = Logger.getLogger(UpgradeCycleNetwork.class);

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments:\n" +
                    "(0) MATSim network file (.xml)\n" +
                    "(1) MATSim output file (.xml)\n" +
                    "(2) CSV of edgeIDs to modify");
        }

        String networkInputPath = args[0];
        String networkOutputPath = args[1];
        String edgeIDsPath = args[2];

        String recString;

        List<Integer> edgeIDs = new ArrayList<>();

        // Read Original MATSim network
        log.info("Reading MATSim network...");
        Network fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(networkInputPath);

        //  READ EDGEIDS
        BufferedReader in = new BufferedReader(new FileReader(edgeIDsPath));

        recString = in.readLine();
        if(!recString.equals("edgeIDs")) {
            throw new RuntimeException("Unknown row header " + recString);
        }

        while ((recString = in.readLine()) != null) {
            edgeIDs.add(Integer.parseInt(recString));
        }

        log.info("Read " + edgeIDs.size() + " edgeIDs");


        // MODIFY NETWORK
        for(Link l : fullNetwork.getLinks().values()) {
            int edgeID = (int) l.getAttributes().getAttribute("edgeID");
            if(edgeIDs.contains(edgeID)) {
                l.getAttributes().putAttribute("cycleosm","offroad");
                l.getToNode().getAttributes().putAttribute("cyc_cros","signal");
                l.getToNode().getAttributes().putAttribute("ped_cros","signal");
            }
        }

        // Write
        new NetworkWriter(fullNetwork).write(networkOutputPath);
    }

}
