package network;

import gis.GpkgReader;
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
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.opengis.feature.simple.SimpleFeature;
import resources.Properties;
import resources.Resources;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.matsim.api.core.v01.TransportMode.*;

// Script to create a MATSim Road network .xml file using the Edges and Nodes from JIBE WP2
// Compatible with JIBE Network v3.12

public class CreateMatsimNetworkRoad {

    private final static Logger log = Logger.getLogger(CreateMatsimNetworkRoad.class);

    public static void main(String[] args) {

        if (args.length != 1) {
            throw new RuntimeException("Program requires 1 argument: Properties file");
        }

        Resources.initializeResources(args[0]);

        final String networkFile = Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK);

        // Read nodes and edges
        Map<Integer, SimpleFeature> nodes = GpkgReader.readNodes();
        Map<Integer, SimpleFeature> edges = GpkgReader.readEdges();

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
        new NetworkWriter(net).write(networkFile);
    }

    private static void addNodeToNetwork(int nodeID, SimpleFeature point, Network net, NetworkFactory fac) {
        int z = (int) point.getAttribute("z_coor");
        Point p = (Point) point.getAttributes().get(0);
        Node node = fac.createNode(Id.createNodeId(nodeID), new Coord(p.getX(),p.getY(),z));

        // Cycle crossing type
        String cycleCrossing = (String) point.getAttribute("cyc_cros");
        if(cycleCrossing == null) {
            cycleCrossing = "null";
        }
        node.getAttributes().putAttribute("bikeCrossing",cycleCrossing);

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

        // Don't include edgeID 220558 because it's extremely short and causes routing errors
        // In future network versions this edge will be eliminated
        if(origNodeId != destNodeId && edgeID != 220558) {
            String roadType = (String) edge.getAttribute("roadtyp");
            String oneWaySummary = (String) edge.getAttribute("onwysmm");
            String modalFilter = (String) edge.getAttribute("modalfl");


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
            l2.getAttributes().putAttribute("osmID",osmID);

            // Name
            String name = (String) edge.getAttribute("name");
            if(name == null) name = "";
            l1.getAttributes().putAttribute("name",name);
            l2.getAttributes().putAttribute("name",name);

            // Out or return
            l1.getAttributes().putAttribute("fwd",true);
            l2.getAttributes().putAttribute("fwd",false);

            // Length
            l1.setLength(length);
            l2.setLength(length);

            // Allowed modes out
            Set<String> allowedModesOut = new HashSet<>();
            switch(roadType) {
                case "Shared Bus Lane":
                    allowedModesOut.add("bus");
                case "Pedestrian Path - Cycling Forbidden":
                case "Path - Cycling Forbidden":
                case "Cycleway":
                case "Segregated Cycleway":
                case "Shared Path":
                case "Segregated Shared Path":
                    allowedModesOut.add(walk);
                    allowedModesOut.add(bike);
                    break;
                case "Living Street":
                case "Residential Road - Cycling Allowed":
                case "Minor Road - Cycling Allowed":
                case "Main Road - Cycling Allowed":
                case "Main Road Link - Cycling Allowed":
                case "Trunk Road Link - Cycling Allowed":
                case "Trunk Road - Cycling Allowed":
                    allowedModesOut.add(walk);
                    allowedModesOut.add(bike);
                    allowedModesOut.add(car);
                    allowedModesOut.add(truck);
                    break;
                case "Special Road - Cycling Forbidden":
                case "motorway_link - Cycling Forbidden":
                case "motorway - Cycling Forbidden":
                    allowedModesOut.add(car);
                    allowedModesOut.add(truck);
                    break;
                default:
                    throw new RuntimeException("Road type " + roadType + " not recognised!");
            }

            // Don't allow car if there's a modal filter
            if(!modalFilter.equals("all")) {
                allowedModesOut.remove(car);
                allowedModesOut.remove(truck);
            }

            // Set allowed modes out
            l1.setAllowedModes(allowedModesOut);

            // Allowed modes return
            Set<String> allowedModesRtn = new HashSet<>(allowedModesOut);
            switch(oneWaySummary) {
                case "One Way":
                    allowedModesRtn.remove(bike);
                case "One Way - Two Way Cycling":
                    allowedModesRtn.remove(car);
                    allowedModesRtn.remove(truck);
                    break;
            }
            l2.setAllowedModes(allowedModesRtn);

            // Are cars allowed on this link? (necessary for mode-specific filtered networks)
            boolean allowsCarOut = allowedModesOut.contains(car);
            boolean allowsCarRtn = allowedModesRtn.contains(car);
            boolean allowsCar = allowsCarOut || allowsCarRtn;

            // Are cars allowed in either direction?
            l1.getAttributes().putAttribute("allowsCar",allowsCar);
            l2.getAttributes().putAttribute("allowsCar",allowsCar);

            // Are cars allowed in the forward direction?
            l1.getAttributes().putAttribute("allowsCarFwd", allowsCarOut);
            l2.getAttributes().putAttribute("allowsCarFwd", allowsCarRtn);

            // Speed limit
            int speedLimit = (int) edge.getAttribute("maxsped");
            l1.getAttributes().putAttribute("speedLimitMPH",speedLimit);
            l2.getAttributes().putAttribute("speedLimitMPH",speedLimit);

            // Freespeed (use speed limit)
            double freespeed = speedLimit * 0.44704;
            l1.setFreespeed(freespeed);
            l2.setFreespeed(freespeed);

            // Add AADT, width, and number of lanes attribute
            Double aadt = (Double) edge.getAttribute("aadt_hgv_im");
            double aadtOut = 0.;
            double aadtRtn = 0.;
            if(aadt == null) {
                aadt = Double.NaN;
                aadtOut = Double.NaN;
                aadtRtn = Double.NaN;
            } else if (allowsCarOut) {
                if (allowsCarRtn) {
                    aadtOut = aadt / 2.;
                    aadtRtn = aadtOut;
                } else {
                    aadtOut = aadt;
                }
            } else {
                aadt = 0.;
            }
            l1.getAttributes().putAttribute("aadt",aadt);
            l2.getAttributes().putAttribute("aadt",aadt);
            l1.getAttributes().putAttribute("aadtFwd",aadtOut);
            l2.getAttributes().putAttribute("aadtFwd",aadtRtn);

            // Width
            double widthOut = (double) edge.getAttribute("avg_wdt_mp");
            double widthRtn = 0.;

            if(allowsCarRtn || (!allowsCarOut && !allowedModesRtn.isEmpty())) {
                widthOut /= 2.;
                widthRtn = widthOut;
            }

            l1.getAttributes().putAttribute("width",widthOut);
            l2.getAttributes().putAttribute("width",widthRtn);

            // Width and number of lanes
            double lanesOut = allowsCarOut ? estimateNumberOflanes(widthOut) : 1.;
            double lanesRtn = allowsCarRtn ? estimateNumberOflanes(widthRtn) : 1.;
            l1.setNumberOfLanes(lanesOut);
            l2.setNumberOfLanes(lanesRtn);

            // Capacity
            int laneCapacity;
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
            String cycleosm = (String) edge.getAttribute("cyclesm");
            if(cycleosm == null) {
                cycleosm = "null";
            }
            l1.getAttributes().putAttribute("cycleosm",cycleosm);
            l2.getAttributes().putAttribute("cycleosm",cycleosm);

            // Surface
            String surface = (String) edge.getAttribute("surface");
            if(surface == null) {
                surface = "unknown";
            }
            l1.getAttributes().putAttribute("surface",surface);
            l2.getAttributes().putAttribute("surface",surface);

            // Strava speeds
            putDoubleAttribute(edge, "sped_b_f", l1,"stravaBikeSpeed", Double.NaN);
            putDoubleAttribute(edge, "sped_b_b", l2,"stravaBikeSpeed", Double.NaN);
            putDoubleAttribute(edge, "sped_p_f", l1,"stravaWalkSpeed", Double.NaN);
            putDoubleAttribute(edge, "sped_p_b", l2,"stravaWalkSpeed", Double.NaN);

            // Strava volumes
            putDoubleAttribute(edge, "aamb_f", l1,"stravaBikeVol", Double.NaN);
            putDoubleAttribute(edge, "aamb_b", l2,"stravaBikeVol", Double.NaN);
            putDoubleAttribute(edge, "aamp_f", l1,"stravaWalkVol", Double.NaN);
            putDoubleAttribute(edge, "aamp_b", l2,"stravaWalkVol", Double.NaN);

            // Type
            l1.getAttributes().putAttribute("type",edge.getAttribute("highway"));
            l2.getAttributes().putAttribute("type",edge.getAttribute("highway"));

            // Is the road a motorway?
            boolean motorway = roadType.contains("motorway");
            l1.getAttributes().putAttribute("motorway",motorway);
            l2.getAttributes().putAttribute("motorway",motorway);

            // Is the road a trunk road?
            boolean trunk = roadType.contains("Trunk") || roadType.contains("motorway");
            l1.getAttributes().putAttribute("trunk",trunk);
            l2.getAttributes().putAttribute("trunk",trunk);

            // Do cyclists have to dismount?
            boolean dismount = roadType.contains("Cycling Forbidden") || cycleosm.equals("dismount");
            l1.getAttributes().putAttribute("dismount",dismount);
            l2.getAttributes().putAttribute("dismount",dismount);

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
            if(veh85percSpeedKPH == null || !allowsCar) veh85percSpeedKPH = Double.NaN;
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
            Integer streetLights = (Integer) edge.getAttribute("strtlgh");
            if(streetLights == null) streetLights = 0;
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

            // Add links to network
            net.addLink(l1);
            if(!l2.getAllowedModes().isEmpty()) {
                net.addLink(l2);
            }
        }
    }

    private static double estimateNumberOflanes(double width) {
        if (width <= 6.5) {
            return 1.;
        } else if (width <= 9.3) {
            return 2.;
        } else {
            return 3.;
        }
    }

    private static void addCrossingAttributes(Network net) {
        Map<Node, List<Link>> linksTo = net.getNodes().values().stream().collect(Collectors.toMap(n -> n, n -> new ArrayList<>()));
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
            double crossAadt = Double.NaN;
            double crossWidth = Double.NaN;
            double crossLanes = Double.NaN;
            double crossSpeedLimit = Double.NaN;
            double cross85PercSpeed = Double.NaN;

            if(endsAtJct) {
                int osmID = (int) link.getAttributes().getAttribute("osmID");
                String name = (String) link.getAttributes().getAttribute("name");
                Set<Link> crossingLinks = linksTo.get(link.getToNode())
                        .stream()
                        .filter(l -> (boolean) l.getAttributes().getAttribute("allowsCarFwd"))
                        .filter(l -> !isMatchingRoad(l,osmID,name))
                        .collect(Collectors.toSet());

                if(!crossingLinks.isEmpty()) {

                    crossVehicles = true;
                    crossWidth = crossingLinks.stream()
                            .mapToDouble(l -> (double) l.getAttributes().getAttribute("width"))
                            .sum();
                    crossLanes = crossingLinks.stream()
                            .mapToDouble(Link::getNumberOfLanes)
                            .sum();
                    crossAadt = crossingLinks.stream()
                            .mapToDouble(l -> (double) l.getAttributes().getAttribute("aadtFwd"))
                            .sum();
                    crossSpeedLimit = crossingLinks.stream()
                            .mapToInt(l -> (int) l.getAttributes().getAttribute("speedLimitMPH"))
                            .max().getAsInt();
                    cross85PercSpeed = crossingLinks.stream()
                            .mapToDouble(l -> (double) l.getAttributes().getAttribute("veh85percSpeedKPH"))
                            .max().getAsDouble();
                }
            }

            link.getAttributes().putAttribute("endsAtJct",endsAtJct);
            link.getAttributes().putAttribute("crossVehicles",crossVehicles);
            link.getAttributes().putAttribute("crossWidth",crossWidth);
            link.getAttributes().putAttribute("crossLanes",crossLanes);
            link.getAttributes().putAttribute("crossAadt",crossAadt);
            link.getAttributes().putAttribute("crossSpeedLimitMPH",crossSpeedLimit);
            link.getAttributes().putAttribute("cross85PercSpeed",cross85PercSpeed);
        }
    }

    private static void putDoubleAttribute(SimpleFeature edge, String name, Link link, String matsimName, double valueIfNull) {
        Double attr = (Double) edge.getAttribute(name);
        if(attr == null) {
            attr = valueIfNull;
        }
        link.getAttributes().putAttribute(matsimName,attr);
    }

    private static boolean isMatchingRoad(Link link, int osmID, String name) {
        boolean osmIdMatch = link.getAttributes().getAttribute("osmID").equals(osmID);
        boolean nameMatch = link.getAttributes().getAttribute("name").equals(name);
        if(!name.equals("")) {
            return nameMatch || osmIdMatch;
        } else {
            return osmIdMatch;
        }
    }
}