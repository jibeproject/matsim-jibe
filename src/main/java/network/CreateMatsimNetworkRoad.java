package network;

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
import java.util.stream.Stream;

import static org.matsim.api.core.v01.TransportMode.*;

// Script to create a MATSim Road network .xml file using the Edges and Nodes from JIBE WP2

public class CreateMatsimNetworkRoad {

    public static void main(String[] args) {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Network nodes and edges file (.gpkg) \n" +
                    "(1) Output MATSim network file (.xml) \n");
        }

        final File inputFile = new File(args[0]);
        final String outputFile = args[1];

        // Read nodes and edges
        Map<String, Map<Integer,SimpleFeature>> networkGpkg = GpkgReader.read(inputFile);

        Map<Integer,SimpleFeature> nodes = networkGpkg.get("nodes");
        Map<Integer,SimpleFeature> edges = networkGpkg.get("edges");

        // MATSim setup
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        Network net = scenario.getNetwork();
        NetworkFactory fac = net.getFactory();

        // Create network nodes
        nodes.forEach((id,node) -> addNodeToNetwork(id,node,net,fac));

        // Create network links
        edges.forEach((id,edge) -> addLinkToNetwork(id,edge,net,fac));

        // Write crossing attributes
        addCrossingAttributes(net);

        // Identify disconnected links
        NetworkUtils2.identifyDisconnectedLinks(net,walk);
        NetworkUtils2.identifyDisconnectedLinks(net,bike);
        NetworkUtils2.identifyDisconnectedLinks(net,car);

        // Write network
        new NetworkWriter(net).write(outputFile);
    }

    private static void addNodeToNetwork(int nodeID, SimpleFeature point, Network net, NetworkFactory fac) {
        double z = (double) point.getAttribute("z_coor");
        Point p = (Point) point.getAttributes().get(0);
        Node node = fac.createNode(Id.createNodeId(nodeID), new Coord(p.getX(),p.getY(),z));

        // Walk crossing type
        String pedCrossing = (String) point.getAttribute("ped_cros");
        if(pedCrossing == null) {
            pedCrossing = "null";
        }
        node.getAttributes().putAttribute("walkCrossing",pedCrossing);

        net.addNode(node);
    }

    private static void addLinkToNetwork(int edgeID, SimpleFeature edge, Network net, NetworkFactory fac) {
        double length = (double) edge.getAttribute("length");
        int origNodeId =  (int) edge.getAttribute("from");
        int destNodeId = (int) edge.getAttribute("to");

        if(origNodeId != destNodeId) {
            String roadType = (String) edge.getAttribute("roadtyp");

            Node origNode = net.getNodes().get(Id.createNodeId(origNodeId));
            Node destNode = net.getNodes().get(Id.createNodeId(destNodeId));

            Link l1 = fac.createLink(Id.createLinkId(edgeID + "out"), origNode, destNode);
            Link l2 = fac.createLink(Id.createLinkId(edgeID + "rtn"), destNode, origNode);

            // Original Edge ID
            l1.getAttributes().putAttribute("edgeID",edgeID);
            l2.getAttributes().putAttribute("edgeID",edgeID);

            // OSM ID
            String osmID = (String) edge.getAttribute("osmid");
            l1.getAttributes().putAttribute("osmID",osmID);
            l2.getAttributes().putAttribute("osmID",osmID);

            // Out or return
            l1.getAttributes().putAttribute("fwd",true);
            l2.getAttributes().putAttribute("fwd",false);

            // Length
            l1.setLength(length);
            l2.setLength(length);

            // Allowed modes out
            Set<String> allowedModes = new HashSet<>();
            switch(roadType) {
                case "Shared Bus Lane":
                    allowedModes.add("bus");
                case "Pedestrian Path - Cycling Forbidden":
                case "Path - Cycling Forbidden":
                case "Cycleway":
                case "Segregated Cycleway":
                case "Shared Path":
                case "Segregated Shared Path":
                    allowedModes.add(walk);
                    allowedModes.add(bike);
                    break;
                case "Living Street":
                case "Residential Road - Cycling Allowed":
                case "Minor Road - Cycling Allowed":
                case "Main Road - Cycling Allowed":
                case "Main Road Link - Cycling Allowed":
                case "Trunk Road Link - Cycling Allowed":
                case "Trunk Road - Cycling Allowed":
                    allowedModes.add(walk);
                    allowedModes.add(bike);
                    allowedModes.add(car);
                    break;
                case "Special Road - Cycling Forbidden":
                case "motorway_link - Cycling Forbidden":
                case "motorway - Cycling Forbidden":
                    allowedModes.add(car);
                    break;
                default:
                    throw new RuntimeException("Road type " + roadType + " not recognised!");
            }
            l1.setAllowedModes(allowedModes);
            l2.setAllowedModes(allowedModes);

            // Are cars allowed on this link? (necessary for mode-specific filtered networks)
            boolean allowsCar = allowedModes.contains(car);

            // Are cars allowed in either direction?
            l1.getAttributes().putAttribute("allowsCar",allowsCar);
            l2.getAttributes().putAttribute("allowsCar",allowsCar);

            // Speed limit in miles per hour
            Integer speedLimit = (Integer) edge.getAttribute("maxspeed");
            if (speedLimit == null) {
                switch(roadType) {
                    case "Pedestrian Path - Cycling Forbidden":
                    case "Path - Cycling Forbidden":
                    case "Shared Path":
                    case "Segregated Shared Path":
                    case "Living Street":
                    case "Cycleway":
                    case "Segregated Cycleway":
                        speedLimit = 10;
                        break;
                    case "Minor Road - Cycling Allowed":
                        speedLimit = 20;
                        break;
                    case "Trunk Road Link - Cycling Allowed":
                    case "Trunk Road - Cycling Allowed":
                    case "Residential Road - Cycling Allowed":
                    case "Main Road - Cycling Allowed":
                    case "Main Road Link - Cycling Allowed":
                        speedLimit = 30;
                        break;
                    case "Special Road - Cycling Forbidden":
                    case "Shared Bus Lane":
                        speedLimit = 60;
                        break;
                    case "motorway_link - Cycling Forbidden":
                    case "motorway - Cycling Forbidden":
                        speedLimit = 70;
                        break;
                    default:
                        throw new RuntimeException("Road type " + roadType + " not recognised!");
                }
            }
            l1.getAttributes().putAttribute("speedLimitMPH",speedLimit);
            l2.getAttributes().putAttribute("speedLimitMPH",speedLimit);

            // Freespeed (use speed limit, convert miles/hr to meters/sec)
            double freespeed = speedLimit * 0.44704;
            l1.setFreespeed(freespeed);
            l2.setFreespeed(freespeed);

            // Capacity and number of lanes
            int laneCapacity;
            int lanes;
            switch(roadType) {
                case "Pedestrian Path - Cycling Forbidden":
                case "Path - Cycling Forbidden":
                case "Shared Path":
                case "Segregated Shared Path":
                case "Living Street":
                case "Cycleway":
                case "Segregated Cycleway":
                case "Shared Bus Lane":
                    laneCapacity = 300;
                    lanes = 1;
                    break;
                case "Special Road - Cycling Forbidden":
                case "Residential Road - Cycling Allowed":
                    laneCapacity = 600;
                    lanes = 1;
                    break;
                case "Minor Road - Cycling Allowed":
                    laneCapacity = 1000;
                    lanes = 1;
                    break;
                case "Main Road - Cycling Allowed":
                case "Main Road Link - Cycling Allowed":
                case "Trunk Road Link - Cycling Allowed":
                case "motorway_link - Cycling Forbidden":
                    laneCapacity = 1500;
                    lanes = 1;
                    break;
                case "Trunk Road - Cycling Allowed":
                case "motorway - Cycling Forbidden":
                    laneCapacity = 2000;
                    lanes = 2;
                    break;
                default:
                    throw new RuntimeException("Road type " + roadType + " not recognised!");
            }
            l1.setNumberOfLanes(lanes);
            l2.setNumberOfLanes(lanes);
            l1.setCapacity(laneCapacity * lanes);
            l2.setCapacity(laneCapacity * lanes);

            // Is the road a motorway?
            boolean motorway = roadType.contains("motorway");
            l1.getAttributes().putAttribute("motorway",motorway);
            l2.getAttributes().putAttribute("motorway",motorway);

            // Viewshed Greenness Visibility Index (VGVI) attribute
            Double vgvi = (Double) edge.getAttribute("VGVI_mean");
            if(vgvi == null) {
                vgvi = 0.;
            }
            l1.getAttributes().putAttribute("vgvi",vgvi);
            l2.getAttributes().putAttribute("vgvi",vgvi);

            // Street lighting
            Integer streetLights = (Integer) edge.getAttribute("strtlght");
            if(streetLights == null) {
                streetLights = 0;
            }
            l1.getAttributes().putAttribute("streetLights",streetLights);
            l2.getAttributes().putAttribute("streetLights",streetLights);

            // Add links to network
            net.addLink(l1);
            if(!l2.getAllowedModes().isEmpty()) {
                net.addLink(l2);
            }
        }
    }

    private static void addCrossingAttributes(Network net) {
        Map<Node,List<Link>> linksTo = net.getNodes().values().stream().collect(Collectors.toMap(n -> n, n -> new ArrayList<>()));
        Map<Node,List<Link>> linksFrom = net.getNodes().values().stream().collect(Collectors.toMap(n -> n, n -> new ArrayList<>()));
        Map<Node,Boolean> isJunction = net.getNodes().values().stream().collect(Collectors.toMap(n -> n, n -> false));

        // List links arriving at each node
        for (Link link : net.getLinks().values()) {
            linksTo.get(link.getToNode()).add(link);
            linksFrom.get(link.getFromNode()).add(link);
        }

        // Check whether each link is a junction (i.e. there are more than 2 links connected to node)
        for (Node node : net.getNodes().values()) {
            boolean junction = Stream.concat(linksFrom.get(node).stream(),linksTo.get(node).stream())
                    .mapToInt(l -> (int) l.getAttributes().getAttribute("edgeID"))
                    .distinct()
                    .count() > 2;
            isJunction.put(node,junction);
        }

        for (Link link : net.getLinks().values()) {

            boolean endsAtJct = isJunction.get(link.getToNode());
            boolean crossVehicles = false;
            double crossLanes = Double.NaN;
            double crossSpeedLimit = Double.NaN;

            if(endsAtJct) {
                String osmID = (String) link.getAttributes().getAttribute("osmID");
                Set<Link> crossingLinks = linksTo.get(link.getToNode())
                        .stream()
                        .filter(l -> (boolean) l.getAttributes().getAttribute("allowsCar"))
                        .filter(l -> !link.getAttributes().getAttribute("osmID").equals(osmID))
                        .collect(Collectors.toSet());

                if(!crossingLinks.isEmpty()) {

                    crossVehicles = true;

                    crossLanes = crossingLinks.stream()
                            .mapToDouble(Link::getNumberOfLanes)
                            .sum();
                    crossSpeedLimit = crossingLinks.stream()
                            .mapToInt(l -> (int) l.getAttributes().getAttribute("speedLimitMPH"))
                            .max().getAsInt();
                }
            }

            link.getAttributes().putAttribute("endsAtJct",endsAtJct);
            link.getAttributes().putAttribute("crossVehicles",crossVehicles);
            link.getAttributes().putAttribute("crossLanes",crossLanes);
            link.getAttributes().putAttribute("crossSpeedLimitMPH",crossSpeedLimit);
        }
    }
}