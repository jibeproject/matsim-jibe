package network;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.NetworkWriter;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class CreateMatsimNetworkVehicle {
    private final static double URBAN_NONPRIMARY_CAPACITY_REDUCTION_FACTOR = 0.25;
    private final static double URBAN_NONPRIMARY_FREESPEED_REDUCTION_FACTOR = 0.25;
    private final static Logger log = Logger.getLogger(CreateMatsimNetworkVehicle.class);
    private static final List<String> PAIRS_TO_CONNECT = List.of("227825out","224795out","164749out","298027out",
            "220563out","128831out","367168out","273137out","124102out","124103out","81480out","8582out","4084out","4083out",
            "224706out","419out","206836out","8823out","349287out","13111out","409267out","409269out","58003out","58867out");

    public static void main(String[] args) throws FactoryException, IOException {
        if(args.length > 2) {
            throw new RuntimeException("Program requires 2 arguments:\n" +
                    "(0) Properties file \n" +
                    "(1) Output gpkg [optional] \n");
        }

        Resources.initializeResources(args[0]);

        // Read MATSim network
        log.info("Reading MATSim network...");
        Network fullNetwork = NetworkUtils2.readFullNetwork();

        // Filter network to a specific mode & clean
        Network vehicleNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(fullNetwork).filter(vehicleNetwork, Set.of(TransportMode.car,TransportMode.truck));
        createConnectors(vehicleNetwork);
        NetworkUtils.runNetworkCleaner(vehicleNetwork);

        // Double capacity of short links
        for (Link link : vehicleNetwork.getLinks().values()) {

            if(link.getLength() < 100.) {
                link.setCapacity(2 * link.getCapacity());
            }

            boolean urban = (boolean) link.getAttributes().getAttribute("urban");
            boolean primary = (boolean) link.getAttributes().getAttribute("primary");
            if(urban && !primary) {
                link.setCapacity((1-URBAN_NONPRIMARY_CAPACITY_REDUCTION_FACTOR) * link.getCapacity());
                link.setFreespeed((1-URBAN_NONPRIMARY_FREESPEED_REDUCTION_FACTOR) * link.getFreespeed());
            }
        }

        // Write car network
        new NetworkWriter(vehicleNetwork).write(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));

        // Write as gpkg
        if(args.length == 2) {
            WriteNetworkGpkgSimple.write(vehicleNetwork,args[1]);
        }
    }

    private static void createConnectors(Network net) {
        NetworkFactory fac = net.getFactory();

        for(int i = 0 ; i < PAIRS_TO_CONNECT.size() ; i += 2) {
            Link linkOut = net.getLinks().get(Id.createLinkId(PAIRS_TO_CONNECT.get(i)));
            Link linkIn = net.getLinks().get(Id.createLinkId(PAIRS_TO_CONNECT.get(i+1)));

            Node fromNode = linkOut.getToNode();
            Node toNode = linkIn.getFromNode();

            Link connector = fac.createLink(Id.createLinkId(PAIRS_TO_CONNECT.get(i) + "_" + PAIRS_TO_CONNECT.get(i+1)),linkOut.getToNode(),linkIn.getFromNode());
            connector.setLength(NetworkUtils.getEuclideanDistance(fromNode.getCoord(),toNode.getCoord()));
            connector.setFreespeed(Math.max(linkIn.getFreespeed(),linkOut.getFreespeed()));
            connector.setCapacity(Math.max(linkIn.getCapacity(),linkOut.getCapacity()));
            connector.setNumberOfLanes(Math.max(linkIn.getNumberOfLanes(),linkOut.getNumberOfLanes()));
            connector.getAttributes().putAttribute("motorway",true);
            connector.getAttributes().putAttribute("trunk",true);
            connector.getAttributes().putAttribute("primary",true);
            connector.getAttributes().putAttribute("urban",false);
            connector.getAttributes().putAttribute("fwd",true);
            connector.getAttributes().putAttribute("edgeID",-1);
            connector.setAllowedModes(Set.of(TransportMode.car,TransportMode.truck));

            net.addLink(connector);
        }
    }
}
