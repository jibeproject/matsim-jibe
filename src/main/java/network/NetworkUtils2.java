package network;

// Additional utils beyond NetworkUtils

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import resources.Properties;
import resources.Resources;
import routing.disutility.JibeDisutility;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

public class NetworkUtils2 {

    private final static Logger log = Logger.getLogger(NetworkUtils2.class);

    public static Network readFullNetwork() {
        // Read network
        log.info("Reading MATSim network...");
        String networkPath = Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK);
        Network fullNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(fullNetwork).readFile(networkPath);
        return fullNetwork;
    }

    public static Network readModeSpecificNetwork(String transportMode) {
        return extractModeSpecificNetwork(readFullNetwork(),transportMode);
    }

    // Extracts mode-specific network  (e.g. walk network, car network, cycle network)
    public static Network extractModeSpecificNetwork(Network network, String transportMode) {
        Network modeSpecificNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(modeSpecificNetwork, Collections.singleton(transportMode));
        NetworkUtils.runNetworkCleaner(modeSpecificNetwork);
        return modeSpecificNetwork;
    }

    public static void extractFromNodes(Network network, Set<Id<Node>> nodeIds) {
        IdSet<Node> nodesToRemove = new IdSet<>(Node.class);
        for (Node node : network.getNodes().values()) {
            if(!nodeIds.contains(node.getId())) nodesToRemove.add(node.getId());
        }
        for (Id<Node> nodeId : nodesToRemove) network.removeNode(nodeId);
    }

    public static Set<Id<Node>> getNodesInBoundary(Network network, Geometry boundary) {
        ConcurrentLinkedQueue<Node> allNodes = new ConcurrentLinkedQueue<>(network.getNodes().values());
        Set<Id<Node>> nodesInBoundary = ConcurrentHashMap.newKeySet();
        Counter counter = new Counter("Checking whether node ", " / " + network.getNodes().size() + " is within boundary");
        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0 ; i < numberOfThreads ; i++) {
            NodeWorker worker = new NodeWorker(allNodes,nodesInBoundary,boundary,counter);
            threads[i] = new Thread(worker,"NodeProcessor-" + i);
            threads[i].start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        log.info("Identified " + nodesInBoundary.size() + " nodes within boundary.");
        return Set.copyOf(nodesInBoundary);
    }

    private static class NodeWorker implements Runnable {

        private static final GeometryFactory gf = new GeometryFactory();
        private final ConcurrentLinkedQueue<Node> allNodes;
        private final Set<Id<Node>> nodesInBoundary;
        private final Geometry boundary;
        private final Counter counter;

        NodeWorker(ConcurrentLinkedQueue<Node> allNodes, Set<Id<Node>> nodesInBoundary, Geometry boundary, Counter counter) {
            this.allNodes = allNodes;
            this.nodesInBoundary = nodesInBoundary;
            this.boundary = boundary;
            this.counter = counter;
        }
        public void run() {
            while(true) {
                Node node = this.allNodes.poll();
                if(node == null) {
                    return;
                }
                this.counter.incCounter();
                Coord c = node.getCoord();
                if(boundary.contains(gf.createPoint(new Coordinate(c.getX(),c.getY())))) {
                    nodesInBoundary.add(node.getId());
                }
            }
        }
    }



    public static void identifyDisconnectedLinks(Network network, String transportMode) {
        Network modeSpecificNetwork = extractModeSpecificNetwork(network, transportMode);
        for(Link link : network.getLinks().values()) {
            boolean disconnected = false;
            if (link.getAllowedModes().contains(transportMode)) {
                disconnected = !modeSpecificNetwork.getLinks().containsKey(link.getId());
            }
            link.getAttributes().putAttribute("disconnected_" + transportMode, disconnected);
        }
    }

    // Extracts network of usable nearest links to start/end journey (e.g. a car trip cannot start on a motorway)
    public static Network extractXy2LinksNetwork(Network network, Predicate<Link> xy2linksPredicate) {
        Network xy2lNetwork = NetworkUtils.createNetwork();
        NetworkFactory nf = xy2lNetwork.getFactory();
        for (Link link : network.getLinks().values()) {
            if (xy2linksPredicate.test(link)) {
                // okay, we need that link
                Node fromNode = link.getFromNode();
                Node xy2lFromNode = xy2lNetwork.getNodes().get(fromNode.getId());
                if (xy2lFromNode == null) {
                    xy2lFromNode = nf.createNode(fromNode.getId(), fromNode.getCoord());
                    xy2lNetwork.addNode(xy2lFromNode);
                }
                Node toNode = link.getToNode();
                Node xy2lToNode = xy2lNetwork.getNodes().get(toNode.getId());
                if (xy2lToNode == null) {
                    xy2lToNode = nf.createNode(toNode.getId(), toNode.getCoord());
                    xy2lNetwork.addNode(xy2lToNode);
                }
                Link xy2lLink = nf.createLink(link.getId(), xy2lFromNode, xy2lToNode);
                xy2lLink.setAllowedModes(link.getAllowedModes());
                xy2lLink.setCapacity(link.getCapacity());
                xy2lLink.setFreespeed(link.getFreespeed());
                xy2lLink.setLength(link.getLength());
                xy2lLink.setNumberOfLanes(link.getNumberOfLanes());
                xy2lLink.getAttributes().putAttribute("edgeID",link.getAttributes().getAttribute("edgeID"));
                xy2lLink.getAttributes().putAttribute("fwd",link.getAttributes().getAttribute("fwd"));
                xy2lNetwork.addLink(xy2lLink);
            }
        }
        return xy2lNetwork;
    }

    public static Map<Id<Link>,Double> precalculateLinkMarginalDisutilities(Network network, TravelDisutility disutility, double time, Person person, Vehicle vehicle) {
        log.info("Precalculating marginal disutilities for each link...");
        IdMap<Link,Double> marginalDisutilities = new IdMap<>(Link.class,network.getLinks().size());
        Counter counter = new Counter("Processing node "," / " + network.getLinks().size());
        for(Link link : network.getLinks().values()) {
            counter.incCounter();
            double linkDisutility = disutility.getLinkTravelDisutility(link,time,person,vehicle);
            if(disutility instanceof JibeDisutility) {
                linkDisutility -= ((JibeDisutility) disutility).getJunctionComponent(link); // todo: check this is happening
            }
            marginalDisutilities.put(link.getId(), linkDisutility / link.getLength());
        }
        return Collections.unmodifiableMap(marginalDisutilities);
    }
}
