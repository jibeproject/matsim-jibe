package network;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.io.NetworkWriter;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.IOException;
import java.util.List;

public class CreateMatsimNetworkManchester {
    private final static double CAPACITY_REDUCTION_FACTOR = 0.3;
    private final static double FREESPEED_REDUCTION_FACTOR = 0.25;
    private static final List<String> LINK_PAIRS_TO_CONNECT = List.of("227825out","224795out","164749out","298027out",
            "220563out","128831out","367168out","273137out","124102out","124103out","81480out","8582out","4084out","4083out",
            "224706out","419out","206836out","8823out","349287out","13111out","409267out","409269out","58003out","58867out");

    public static void main(String[] args) throws FactoryException, IOException {
        if(args.length > 2) {
            throw new RuntimeException("Program requires 2 arguments:\n" +
                    "(0) Properties file \n" +
                    "(1) Include previous simulation details in network? (true/false)");
        }

        Resources.initializeResources(args[0]);
        Network net = NetworkGpkgToMatsim.convertNetwork(Boolean.parseBoolean(args[1]), LINK_PAIRS_TO_CONNECT);

        // MANCHESTER-SPECIFIC ADJUSTMENTS
        for (Link link : net.getLinks().values()) {

            // Reduce capacity for primary and secondary links
            boolean primary = (boolean) link.getAttributes().getAttribute("primary");
            String type = (String) link.getAttributes().getAttribute("type");
            boolean secondary = type != null && type.contains("secondary");
            if(primary || secondary) {
                link.setCapacity((1-CAPACITY_REDUCTION_FACTOR) * link.getCapacity());
            }

            // Reduce freespeed for urban links
            boolean urban = (boolean) link.getAttributes().getAttribute("urban");
            if(urban) {
                link.setFreespeed((1-FREESPEED_REDUCTION_FACTOR) * link.getFreespeed());
            }
        }

        // Write network
        new NetworkWriter(net).write(Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK));
    }
}
