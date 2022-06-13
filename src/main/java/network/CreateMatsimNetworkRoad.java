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
import java.util.*;
import java.util.stream.Collectors;

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

        // Write conflicting traffic at junction attribute
        addJunctionAadt(net);

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

            int lanesOut = Integer.max(1, (int) edge.getAttribute("lns_no_f"));
            int lanesRtn = Integer.max(1, (int) edge.getAttribute("lns_no_b"));

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
            l1.getAttributes().putAttribute("cycleway",edge.getAttribute("cyclwy_f"));
            l2.getAttributes().putAttribute("cycleway",edge.getAttribute("cyclwy_b"));

            // OSM Cycle lane type
            String cycleosm = (String) edge.getAttribute("cycleosm");
            if(cycleosm == null) {
                cycleosm = "null";
            }
            l1.getAttributes().putAttribute("cycleosm",cycleosm);
            l2.getAttributes().putAttribute("cycleosm",cycleosm);

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
            Double aadt = (Double) edge.getAttribute("aadt_hgv_im");
            if(aadt == null) aadt = Double.NaN;
            l1.getAttributes().putAttribute("aadt",aadt);
            l2.getAttributes().putAttribute("aadt",aadt);

            // Add NDVImean attribute
            Double ndvi = (Double) edge.getAttribute("NDVImen");
            if(ndvi == null) {
                ndvi = 0.;
                log.warn("Null NDVI for edge " + edgeID + ". Set to 0.");
            }
            l1.getAttributes().putAttribute("ndvi",ndvi);
            l2.getAttributes().putAttribute("ndvi",ndvi);

            // Add VGVI attribute
            Double vgvi = (Double) edge.getAttribute("VGVI_mean");
            if(vgvi == null) {
                vgvi = 0.0293269 + 1.0927493 * ndvi;
            }
            l1.getAttributes().putAttribute("vgvi",vgvi);
            l2.getAttributes().putAttribute("vgvi",vgvi);

            // Car speed
            Double veh85percSpeedKPH = (Double) edge.getAttribute("spedKPH");
            if(veh85percSpeedKPH == null) veh85percSpeedKPH = Double.NaN;
            l1.getAttributes().putAttribute("veh85percSpeedKPH",veh85percSpeedKPH);
            l2.getAttributes().putAttribute("veh85percSpeedKPH",veh85percSpeedKPH);

            // Quietness
            Integer quietness = (Integer) edge.getAttribute("quitnss");
            if(quietness == null) {
                quietness = 10;
                log.warn("Null quietness for edge " + edgeID + ". Set to 0.");
            }
            l1.getAttributes().putAttribute("quietness",quietness);
            l2.getAttributes().putAttribute("quietness",quietness);

            // Street lighting
            double streetLights = (double) edge.getAttribute("strtlgh");
            l1.getAttributes().putAttribute("streetLights",streetLights);
            l2.getAttributes().putAttribute("streetLights",streetLights);

            // Junction
            String junction = (String) edge.getAttribute("junctin");
            l1.getAttributes().putAttribute("junction",junction);
            l2.getAttributes().putAttribute("junction",junction);

            // Shannon diversity index
            Double shannon = (Double) edge.getAttribute("shannon");
            if(shannon == null) shannon = 0.;
            l1.getAttributes().putAttribute("shannon",shannon);
            l2.getAttributes().putAttribute("shannon",shannon);

            // POIs
            Double POIs = (Double) edge.getAttribute("indp_sc");
            if(POIs == null) POIs = 0.;
            l1.getAttributes().putAttribute("POIs",POIs);
            l2.getAttributes().putAttribute("POIs",POIs);

            // Negative POIs
            Double negPOIs = (Double) edge.getAttribute("ngp_scr");
            if(negPOIs == null) negPOIs = 0.;
            l1.getAttributes().putAttribute("negPOIs",negPOIs);
            l2.getAttributes().putAttribute("negPOIs",negPOIs);

            // HVG-generating POIs
            Double hgvPOIs = (Double) edge.getAttribute("negpoi_hgv_score");
            if(hgvPOIs == null) hgvPOIs = 0.;
            l1.getAttributes().putAttribute("hgvPOIs",hgvPOIs);
            l2.getAttributes().putAttribute("hgvPOIs",hgvPOIs);

            // Crime
            double crime = (double) edge.getAttribute("crim_cnt");
            l1.getAttributes().putAttribute("crime",crime);
            l2.getAttributes().putAttribute("crime",crime);

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

    private static void addJunctionAadt(Network net) {
        Map<Node,Double> nodeAadtMap = net.getNodes().values().stream().collect(Collectors.toMap(x -> x, x -> 0.));

        // Store AADT values for every link arriving at each node
        for (Link link : net.getLinks().values()) {
            if((boolean) link.getAttributes().getAttribute("allowsCar")) {
                Node toNode = link.getToNode();
                double oldVal = nodeAadtMap.get(toNode);
                Double val = (Double) link.getAttributes().getAttribute("aadt");
                if(val.isNaN()) {
                    val = 1570.;
                }
                nodeAadtMap.put(toNode, oldVal + val);
            }
        }

        // Store as link attribute
        for (Link link : net.getLinks().values()) {
            Node toNode = link.getToNode();
            Double allLinks = nodeAadtMap.get(toNode);
            Double thisLink;
            if((boolean) link.getAttributes().getAttribute("allowsCar")) {
                thisLink = (Double) link.getAttributes().getAttribute("aadt");
                if(thisLink.isNaN()) {
                    thisLink = 1570.;
                }
            } else {
                thisLink = 0.;
            }
            link.getAttributes().putAttribute("jctAadt",allLinks - thisLink);
        }
    }
}