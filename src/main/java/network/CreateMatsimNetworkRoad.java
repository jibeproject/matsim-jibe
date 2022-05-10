package network;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Script to create a MATSim Road network .xml file using the Edges and Nodes from JIBE WP2

public class CreateMatsimNetworkRoad {

    private final static Logger log = Logger.getLogger(CreateMatsimNetworkRoad.class);

    public static void main(String[] args) {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments: \n" +
                    "(0) Nodes file \n" +
                    "(1) Edges file \n" +
                    "(2) Output network file \n");
        }

        final File nodesFile = new File(args[0]);
        final File edgesFile = new File(args[1]);
        final String networkFile = args[2];

        // Read nodes and edges
        Map<Integer,SimpleFeature> nodes = GpkgReader.readNodes(nodesFile);
        Map<Integer,SimpleFeature> edges = GpkgReader.readEdges(edgesFile);

        // MATSim setup
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        Network net = scenario.getNetwork();
        NetworkFactory fac = net.getFactory();

        // Create network nodes
        nodes.forEach((id,node) -> addNodeToNetwork(id,node,net,fac));

        // Create network links
        edges.forEach((id,edge) -> addLinkToNetwork(id,edge,net,fac));

        // Write network
        new NetworkWriter(net).write(networkFile);
    }

    private static void addNodeToNetwork(int nodeID, SimpleFeature node, Network net, NetworkFactory fac) {
        int z = (int) node.getAttribute("z_coor");
        Point p = (Point) node.getAttributes().get(0);
        net.addNode(fac.createNode(Id.createNodeId(nodeID), new Coord(p.getX(),p.getY(),z)));
    }

    private static void addLinkToNetwork(int edgeID, SimpleFeature edge, Network net, NetworkFactory fac) {
        double length = (double) edge.getAttribute("length");
        int origNodeId =  (int) edge.getAttribute("from");
        int destNodeId = (int) edge.getAttribute("to");

        if(origNodeId != destNodeId) {
            String roadType = (String) edge.getAttribute("roadtyp");
            String oneWaySummary = (String) edge.getAttribute("onwysmm");

            int lanesOut = Integer.max(1, (int) edge.getAttribute("lns_frw"));
            int lanesRtn = Integer.max(1, (int) edge.getAttribute("lns_bck"));

            Node origNode = net.getNodes().get(Id.createNodeId(origNodeId));
            Node destNode = net.getNodes().get(Id.createNodeId(destNodeId));

            Link l1 = fac.createLink(Id.createLinkId(edgeID + "out"), origNode, destNode);
            Link l2 = fac.createLink(Id.createLinkId(edgeID + "rtn"), destNode, origNode);

            // Original Edge ID
            l1.getAttributes().putAttribute("edgeID",edgeID);
            l2.getAttributes().putAttribute("edgeID",edgeID);

            // OSM ID
            int osmID = (int) edge.getAttribute("osm_id");
            l1.getAttributes().putAttribute("osmID",osmID);
            l1.getAttributes().putAttribute("osmID",osmID);

            // Out or return
            l1.getAttributes().putAttribute("fwd",true);
            l2.getAttributes().putAttribute("fwd",false);

            // Number of lanes
            l1.setNumberOfLanes(lanesOut);
            l2.setNumberOfLanes(lanesRtn);

            // Length
            l1.setLength(length);
            l2.setLength(length);

            // Allowed modes out
            Set<String> allowedModesOut = new HashSet<>();
            switch(roadType) {
                case "Path - Cycling Forbidden":
                    allowedModesOut.add("walk");
                    break;
                case "Cycleway":
                case "Segregated Cycleway":
                case "Shared Path":
                case "Segregated Shared Path":
                case "Living Street":
                    allowedModesOut.add("walk");
                    allowedModesOut.add("bike");
                    break;
                case "Residential Road - Cycling Allowed":
                case "Minor Road - Cycling Allowed":
                case "Main Road - Cycling Allowed":
                case "Main Road Link - Cycling Allowed":
                case "Trunk Road Link - Cycling Allowed":
                case "Trunk Road - Cycling Allowed":
                    allowedModesOut.add("walk");
                    allowedModesOut.add("bike");
                    allowedModesOut.add("car");
                    break;
                case "Special Road - Cycling Forbidden":
                case "motorway_link - Cycling Forbidden":
                case "motorway - Cycling Forbidden":
                    allowedModesOut.add("car");
                    break;
                default:
                    throw new RuntimeException("Road type " + roadType + " not recognised!");
            }
            l1.setAllowedModes(allowedModesOut);

            // Allowed modes return
            Set<String> allowedModesRtn = new HashSet<>(allowedModesOut);
            switch(oneWaySummary) {
                case "One Way":
                    allowedModesRtn.remove("bike");
                case "One Way - Two Way Cycling":
                    allowedModesRtn.remove("car");
                    break;
            }
            l2.setAllowedModes(allowedModesRtn);

            // Are cars allowed on this link? (necessary for mode-specific filtered networks)
            l1.getAttributes().putAttribute("allowsCar",l1.getAllowedModes().contains("car"));
            l2.getAttributes().putAttribute("allowsCar",l2.getAllowedModes().contains("car"));

            // Speed limit
            int speedLimit = (int) edge.getAttribute("maxsped");
            l1.getAttributes().putAttribute("speedLimitMPH",speedLimit);
            l2.getAttributes().putAttribute("speedLimitMPH",speedLimit);

            // Freespeed (use speed limit)
            double freespeed = speedLimit * 0.44704;
            l1.setFreespeed(freespeed);
            l2.setFreespeed(freespeed);

            // Capacity
            int laneCapacity;
            switch(roadType) {
                case "Path - Cycling Forbidden":
                case "Shared Path":
                case "Segregated Shared Path":
                case "Living Street":
                case "Cycleway":
                case "Segregated Cycleway":
                    laneCapacity = 300;
                    break;
                case "Special Road - Cycling Forbidden":
                case "Residential Road - Cycling Allowed":
                    laneCapacity = 600;
                    break;
                case "Minor Road - Cycling Allowed":
                    laneCapacity = 1000;
                    break;
                case "Main Road - Cycling Allowed":
                case "Main Road Link - Cycling Allowed":
                case "Trunk Road Link - Cycling Allowed":
                case "motorway_link - Cycling Forbidden":
                    laneCapacity = 1500;
                    break;
                case "Trunk Road - Cycling Allowed":
                case "motorway - Cycling Forbidden":
                    laneCapacity = 2000;
                    break;
                default:
                    throw new RuntimeException("Road type " + roadType + " not recognised!");
            }
            l1.setCapacity(laneCapacity * lanesOut);
            l2.setCapacity(laneCapacity * lanesRtn);

            // Cycle lane
            l1.getAttributes().putAttribute("cycleway",edge.getAttribute("cyclwy_l"));
            l2.getAttributes().putAttribute("cycleway",edge.getAttribute("cyclwy_r"));

            // OSM Cycle lane type
            String cycleosm = (String) edge.getAttribute("cycleosm");
            if(cycleosm == null) {
                cycleosm = "null";
            }
            l1.getAttributes().putAttribute("cycleosm",cycleosm);
            l2.getAttributes().putAttribute("cycleosm",cycleosm);

            // Cycle speed from STRAVA
            Double ttBikeFwd = (Double) edge.getAttribute("tt_bike_fwd");
            Double ttBikeRev = (Double) edge.getAttribute("tt_bike_rev");

            if(ttBikeFwd == null) ttBikeFwd = Double.NaN;
            if(ttBikeRev == null) ttBikeRev = Double.NaN;

            l1.getAttributes().putAttribute("bikeSpeed",length / ttBikeFwd);
            l2.getAttributes().putAttribute("bikeSpeed",length / ttBikeRev);

            // Surface
            String surface = (String) edge.getAttribute("surface");
            if(surface == null) {
                surface = "unknown";
                log.warn("Null surface for edge " + edgeID + ". Labelled as \"unknown\"");
            }
            l1.getAttributes().putAttribute("surface",surface);
            l2.getAttributes().putAttribute("surface",surface);

            // Type
            l1.getAttributes().putAttribute("type",edge.getAttribute("highway"));
            l2.getAttributes().putAttribute("type",edge.getAttribute("highway"));

            // Is the road a motorway?
            boolean motorway = roadType.contains("motorway");
            l1.getAttributes().putAttribute("motorway",motorway);
            l2.getAttributes().putAttribute("motorway",motorway);

            // Add AADT attribute
            double avgAadt = (double) edge.getAttribute("aadt_mp");
            l1.getAttributes().putAttribute("aadt",avgAadt);
            l2.getAttributes().putAttribute("aadt",avgAadt);

            // Add NDVImean attribute
            Double NDVImean = (Double) edge.getAttribute("NDVImen");
            if(NDVImean == null) {
                NDVImean = 0.;
                log.warn("Null NDVI for edge " + edgeID + ". Set to 0.");
            }
            l1.getAttributes().putAttribute("ndvi",NDVImean);
            l2.getAttributes().putAttribute("ndvi",NDVImean);

            // Car speed
            double trafficSpeed = (double) edge.getAttribute("spedKPH");
            l1.getAttributes().putAttribute("trafficSpeedKPH",trafficSpeed);
            l2.getAttributes().putAttribute("trafficSpeedKPH",trafficSpeed);

            // Quietness
            Integer quietness = (Integer) edge.getAttribute("quitnss");
            if(quietness == null) {
                quietness = 0;
                log.warn("Null quietness for edge " + edgeID + ". Set to 0.");
            }
            l1.getAttributes().putAttribute("quietness",quietness);
            l2.getAttributes().putAttribute("quietness",quietness);

            // Average width
            double avgWidth = (double) edge.getAttribute("avg_wdt_mp");
            l1.getAttributes().putAttribute("averageWidth.imp",avgWidth);
            l2.getAttributes().putAttribute("averageWidth.imp",avgWidth);

            // Slope
            double slope = (double) edge.getAttribute("slope");
            l1.getAttributes().putAttribute("slope",slope);
            l2.getAttributes().putAttribute("slope",slope);

            // Add links to network
            net.addLink(l1);
            net.addLink(l2);
        }
    }
}