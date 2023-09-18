package network;

import com.google.common.collect.Sets;
import demand.volumes.VolumeEventHandler;
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
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.opengis.feature.simple.SimpleFeature;
import resources.Properties;
import resources.Resources;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.matsim.api.core.v01.TransportMode.*;

// Script to create a MATSim Road network .xml file using the Edges and Nodes from JIBE WP2
// Compatible with JIBE Network v3.13

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

        // Add volumes from events file
        addSimulationVolumes(net);

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
        String linkModes = (String) edge.getAttribute("modes");

        if(origNodeId != destNodeId && !linkModes.equals("pt")) {
            String roadType = (String) edge.getAttribute("roadtyp");
            String modalFilter = (String) edge.getAttribute("modalfl");

            Node origNode = net.getNodes().get(Id.createNodeId(origNodeId));
            Node destNode = net.getNodes().get(Id.createNodeId(destNodeId));

            Link l1 = fac.createLink(Id.createLinkId(edgeID + "out"), origNode, destNode);
            Link l2 = fac.createLink(Id.createLinkId(edgeID + "rtn"), destNode, origNode);

            // Original Edge ID
            l1.getAttributes().putAttribute("edgeID",edgeID);
            l2.getAttributes().putAttribute("edgeID",edgeID);

            // OSM ID
            putIntegerAttribute(l1,edge,"osm_id","osmID",-1);
            putIntegerAttribute(l2,edge,"osm_id","osmID",-1);

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

            // Freespeed
            double freespeed = (double) edge.getAttribute("freespeed");
            l1.setFreespeed(freespeed);
            l2.setFreespeed(freespeed);

            // Urban or rural
            boolean urban = (boolean) edge.getAttribute("urban");
            l1.getAttributes().putAttribute("urban",urban);
            l2.getAttributes().putAttribute("urban",urban);

            // ALLOWED MODES
            Set<String> allowedModesOut = Sets.newHashSet(linkModes.split(","));

            // Don't allow cars or trucks if there's a modal filter
            if(!"all".equals(modalFilter)) {
                allowedModesOut.remove(car);
                allowedModesOut.remove(truck);
            }

            // Set allowed modes out
            l1.setAllowedModes(allowedModesOut);

            // Allowed modes return
            Set<String> allowedModesRtn = new HashSet<>(allowedModesOut);
            String oneWaySummary = (String) edge.getAttribute("onwysmm");
            if(oneWaySummary.equals("One Way")) {
                allowedModesRtn.remove(bike);
                allowedModesRtn.remove(car);
                allowedModesRtn.remove(truck);
            } else if(oneWaySummary.equals("One Way - Two Way Cycling")) {
                allowedModesRtn.remove(car);
                allowedModesRtn.remove(truck);
            } else  {
                Boolean isOneWay = (Boolean) edge.getAttribute("is_oneway"); // Attribute currently only for Melbourne network
                if(isOneWay != null) {
                    if(isOneWay) {
                        allowedModesRtn.remove(bike);
                        allowedModesRtn.remove(car);
                        allowedModesRtn.remove(truck);
                    }
                }
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

            // Add AADT, width, and number of lanes attribute todo: eliminate this once we've validated MATSim results
            Double aadt = (Double) edge.getAttribute("aadt_hgv_im");
            if(aadt != null) {
                double aadtOut = 0.;
                double aadtRtn = 0.;
                if (allowsCarOut) {
                    if (allowsCarRtn) {
                        aadtOut = aadt / 2.;
                        aadtRtn = aadtOut;
                    } else {
                        aadtOut = aadt;
                    }
                } else {
                    aadt = 0.;
                }
                l1.getAttributes().putAttribute("aadt_old",aadt);
                l2.getAttributes().putAttribute("aadt_old",aadt);
                l1.getAttributes().putAttribute("aadtFwd_old",aadtOut);
                l2.getAttributes().putAttribute("aadtFwd_old",aadtRtn);
            }

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
            double lanes = Math.min((double) edge.getAttribute("permlanes"),8);
            double lanesOut = allowsCarOut ? lanes : 1.;
            double lanesRtn = allowsCarRtn ? lanes : 1.;
            l1.setNumberOfLanes(lanesOut);
            l2.setNumberOfLanes(lanesRtn);

            // Capacity
            double capacity = (double) edge.getAttribute("capacity");
            l1.setCapacity(allowsCarOut ? capacity : 0.);
            l2.setCapacity(allowsCarRtn ? capacity : 0.);

            // Speed limit
            Integer speedLimit = (Integer) edge.getAttribute("maxspeed");
            l1.getAttributes().putAttribute("speedLimitMPH",speedLimit);
            l2.getAttributes().putAttribute("speedLimitMPH",speedLimit);

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
            putDoubleAttribute(l1,edge, "sped_b_f", "stravaBikeSpeed", Double.NaN);
            putDoubleAttribute(l2,edge, "sped_b_b","stravaBikeSpeed", Double.NaN);
            putDoubleAttribute(l1,edge, "sped_p_f", "stravaWalkSpeed", Double.NaN);
            putDoubleAttribute(l2,edge, "sped_p_b", "stravaWalkSpeed", Double.NaN);

            // Strava volumes
            putDoubleAttribute(l1,edge, "aamb_f", "stravaBikeVol", Double.NaN);
            putDoubleAttribute(l2,edge, "aamb_b", "stravaBikeVol", Double.NaN);
            putDoubleAttribute(l1,edge, "aamp_f", "stravaWalkVol", Double.NaN);
            putDoubleAttribute(l2,edge, "aamp_b", "stravaWalkVol", Double.NaN);

            // Type
            l1.getAttributes().putAttribute("type",edge.getAttribute("highway"));
            l2.getAttributes().putAttribute("type",edge.getAttribute("highway"));

            // Is the road a motorway?
            boolean motorway = roadType.contains("motorway");
            l1.getAttributes().putAttribute("motorway",motorway);
            l2.getAttributes().putAttribute("motorway",motorway);

            // Is the road a trunk road?
            boolean trunk = motorway || roadType.contains("Trunk");
            l1.getAttributes().putAttribute("trunk",trunk);
            l2.getAttributes().putAttribute("trunk",trunk);

            // Is the road a primary road?
            boolean primary = trunk || roadType.contains("Main");
            l1.getAttributes().putAttribute("primary",primary);
            l2.getAttributes().putAttribute("primary",primary);

            // Do cyclists have to dismount?
            boolean dismount = roadType.contains("Cycling Forbidden") || cycleosm.equals("dismount");
            l1.getAttributes().putAttribute("dismount",dismount);
            l2.getAttributes().putAttribute("dismount",dismount);

            // Add NDVImean attribute
            Double ndvi = (Double) edge.getAttribute("NDVImen");
            if(ndvi == null) {
                ndvi = 0.;
                // log.warn("Null NDVI for edge " + edgeID + ". Set to 0."); todo: uncomment once NDVI is added to Melbourne dataset
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
            putIntegerAttribute(l1,edge,"quitnss","quietness",10);
            putIntegerAttribute(l2,edge,"quitnss","quietness",10);

            // Junction
            String junction = (String) edge.getAttribute("junctin");
            l1.getAttributes().putAttribute("junction",junction);
            l2.getAttributes().putAttribute("junction",junction);

            // Street lighting
            putIntegerAttribute(l1,edge,"strtlgh","streetLights",0);
            putIntegerAttribute(l2,edge,"strtlgh","streetLights",0);

            // Shannon diversity index
            putDoubleAttribute(l1,edge,"shannon","shannon",0.);
            putDoubleAttribute(l2,edge,"shannon","shannon",0.);

            // POIs
            putIntegerAttribute(l1,edge,"indp_sc","POIs",0);
            putIntegerAttribute(l2,edge,"indp_sc","POIs",0);

            // Negative POIs
            putIntegerAttribute(l1,edge,"ngp_scr","negPOIs",0);
            putIntegerAttribute(l2,edge,"ngp_scr","negPOIs",0);

            // HVG-generating POIs
            putIntegerAttribute(l1,edge,"negpoi_hgv_score","hgvPOIs",0);
            putIntegerAttribute(l2,edge,"negpoi_hgv_score","hgvPOIs",0);

            // Crime
            putIntegerAttribute(l1,edge, "crim_cnt", "crime", 0);
            putIntegerAttribute(l2,edge, "crim_cnt", "crime", 0);

            // Add links to network
            net.addLink(l1);
            if(!l2.getAllowedModes().isEmpty()) {
                net.addLink(l2);
            }
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

    private static void addSimulationVolumes(Network network) {
        log.info("Adding volumes from events...");
        EventsManager eventsManager = new EventsManagerImpl();
        VolumeEventHandler volumeEventHandler = new VolumeEventHandler();
        eventsManager.addHandler(volumeEventHandler);
        EventsUtils.readEvents(eventsManager,"output_old/output_events.xml");
        double scaleFactor =  Resources.instance.getDouble(Properties.MATSIM_TFGM_SCALE_FACTOR);

        // Add forward AADT
        network.getLinks().forEach((id,link) -> link.getAttributes().putAttribute("aadtFwd",volumeEventHandler.getLinkVolumes().getOrDefault(id,0) / scaleFactor));

        // Add forward + opposing AADT
        for(Link link : network.getLinks().values()) {
            Link oppositeLink = NetworkUtils.findLinkInOppositeDirection(link);
            int aadtFwd = (int) link.getAttributes().getAttribute("aadtFwd");
            int aadtOpp = (int) oppositeLink.getAttributes().getAttribute("aadtFwd");
            link.getAttributes().putAttribute("aadt",aadtFwd + aadtOpp);
        }
    }

    private static void putDoubleAttribute(Link link, SimpleFeature edge, String name, String matsimName, double valueIfNull) {
        Double attr = (Double) edge.getAttribute(name);
        if(attr == null) {
            attr = valueIfNull;
        }
        link.getAttributes().putAttribute(matsimName,attr);
    }

    private static void putIntegerAttribute(Link link, SimpleFeature edge, String name, String matsimName, int valueIfNull) {
        Integer attr = (Integer) edge.getAttribute(name);
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