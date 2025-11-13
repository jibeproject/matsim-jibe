package network;

import gis.GpkgReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class CreateMatsimNetworkCopenhagen {

    public static final String NODES_FILE = "copenhagen/nodes.csv";
    public static final String ID_VAR = "osmid";
    public static final String X_VAR = "x";
    public static final String Y_VAR = "y";
    public static final String  SEP = ",";

    public static final String NETWORK_FILE = "copenhagen/net_bike.gpkg";
    public static final String OUTPUT_FILE = "copenhagen/copenhagen.xml";

    public static void main(String[] args) throws FactoryException, IOException {

        // MATSim setup
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        Network net = scenario.getNetwork();
        NetworkFactory fac = net.getFactory();

        // Read Nodes
        BufferedReader in = IOUtils.getBufferedReader(NODES_FILE);
        String recString = in.readLine();
        String[] header = recString.split(SEP);

        int posId = findPositionInArray(ID_VAR,header);
        int posX = findPositionInArray(X_VAR,header);
        int posY = findPositionInArray(Y_VAR,header);

        while((recString = in.readLine()) != null) {
            String[] lineElements = recString.split(SEP);
            net.addNode(fac.createNode(Id.createNodeId(Long.parseLong(lineElements[posId])),
                    new Coord(Double.parseDouble(lineElements[posX]), Double.parseDouble(lineElements[posY]))));
        }
        in.close();

        // Read Links
        Map<Integer, SimpleFeature> edges = GpkgReader.readFeatures(new File(NETWORK_FILE),"Network_ID");
        edges.forEach((id,edge) -> addLinkToNetwork(id,edge,net,fac));

        // Write network
        NetworkUtils.runNetworkCleaner(net);
        new NetworkWriter(net).write(OUTPUT_FILE);
    }

    private static void addLinkToNetwork(int linkId, SimpleFeature edge, Network net, NetworkFactory fac) {

        double length = (double) edge.getAttribute("length");
        long u = (long) ((double) edge.getAttribute("u"));
        long v = (long) ((double) edge.getAttribute("v"));

        Node origNode = net.getNodes().get(Id.createNodeId(u));
        Node destNode = net.getNodes().get(Id.createNodeId(v));

        if(origNode == null || destNode == null) {
            throw new RuntimeException("Origin and/or destination node not found: " + u + " " + v);
        }

        Link l = fac.createLink(Id.createLinkId(linkId), origNode, destNode);

        // Needed for matsim (only length is used for accessibility)
        l.setAllowedModes(Set.of(TransportMode.bike));
        l.setLength(length);
        l.setNumberOfLanes(1);
        l.setCapacity(100);
        l.setFreespeed(100);

        // Other link attributes
        Set<String> numericAttributes = Set.of("no_cycling_expected","cycletrack_expected","WrongWay",
                "Residential","LargeRoad","MediumRoad","Cycleway",
                "Track","Track_LargeRoad","Track_MediumRoad","Track_Residential",
                "Lane","Lane_LargeRoad","Lane_MediumRoad","Lane_Residential",
                "No","No_LargeRoad","No_MediumRoad","No_Residential",
                "Footway","LivingStreet","Steps","SharedPath","Pedestrian",
                "Smooth","Unpaved","Cobble","SurfUnknown",
                "E_exp","EE_acc","EE_up","E_leftover");
        numericAttributes.forEach(attr -> l.getAttributes().putAttribute(attr,edge.getAttribute(attr)));
        net.addLink(l);
    }

    private static int findPositionInArray (String string, String[] array) {
        int ind = -1;
        for (int a = 0; a < array.length; a++) {
            if (array[a].equalsIgnoreCase(string)) {
                ind = a;
            }
        }
        return ind;
    }
}
