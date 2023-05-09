package routing.graph;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;

import java.util.*;

/**
 * Multi-tree based on LeastCostPathTree
 */
public class MultiTree {

    private final SpeedyGraph graph;
    private final double[] data; // 3 entries per node: time, cost, distance
    private final int[] comingFrom;

    private final Map<Integer, Set<TreeNode>> pathsIn;
    private final SpeedyGraph.LinkIterator outLI;
    private final SpeedyGraph.LinkIterator inLI;
    private final NodeMinHeap pq;

    private final Counter c1 = new Counter("Loop 1: ");
    private final Counter c2 = new Counter("Loop 2: ");


    public MultiTree(SpeedyGraph graph) {
        this.graph = graph;
        this.data = new double[graph.nodeCount];
        this.comingFrom = new int[graph.nodeCount];
        this.pathsIn = new HashMap<>(graph.nodeCount);
        this.pq = new NodeMinHeap(graph.nodeCount, this::getCost, this::setCost);
        this.outLI = graph.getOutLinkIterator();
        this.inLI = graph.getInLinkIterator();
    }

    public void calculate(int startNodeIdx, int endNodeIdx, double detourLimit) {
        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFrom, -1);

        setCost(startNodeIdx,0.);

        this.pq.clear();
        this.pq.insert(startNodeIdx);

        while (!this.pq.isEmpty()) {
            final int nodeIdx = this.pq.poll();
            double currCost = getCost(nodeIdx);

            if (currCost > detourLimit * getCost(endNodeIdx)) {
                break;
            }

            this.outLI.reset(nodeIdx);
            while (this.outLI.next()) {
                int linkIdx = this.outLI.getLinkIndex();
                Link link = this.graph.getLink(linkIdx);
                int toNode = this.outLI.getToNodeIndex();

                double oldCost = getCost(toNode);
                double newCost = currCost + link.getLength();

                if (Double.isFinite(oldCost)) {
                    if (newCost < oldCost) {
                        this.pq.decreaseKey(toNode, newCost);
                        setCost(toNode, newCost);
                        this.comingFrom[toNode] = nodeIdx;
                    }
                } else {
                    setCost(toNode, newCost);
                    this.pq.insert(toNode);
                    this.comingFrom[toNode] = nodeIdx;
                }
            }
        }

        double leastCostDist = getCost(endNodeIdx);
        System.out.println("Least cost to node " + endNodeIdx + " = " + leastCostDist);
        System.out.println("Calculated least cost tree! Now testing paths...");

        Coord startCoord = this.graph.getNode(startNodeIdx).getCoord();
        Coord endCoord = this.graph.getNode(endNodeIdx).getCoord();

        System.out.println("Distance from start to end: " + CoordUtils.calcProjectedEuclideanDistance(startCoord,endCoord));

        TreeNode origin = new TreeNode(startNodeIdx, null, null, 0.);
        Set<TreeNode> root = new HashSet<>(1);
        root.add(origin);
        pathsIn.put(startNodeIdx, root);

        explore(origin,getCost(endNodeIdx),startCoord,endCoord,1.04);
    }

    private void explore(TreeNode n,double endCost,Coord startCoord,Coord endCoord,double detourLimit) {
        SpeedyGraph.LinkIterator li = this.graph.getOutLinkIterator();
        li.reset(n.nodeIdx);
        while (li.next()) {
            int linkIdx = li.getLinkIndex();
            int toNodeIdx = li.getToNodeIndex();
            Link link = this.graph.getLink(linkIdx);
            double linkCost = link.getLength();
            if(!isLoop(n,toNodeIdx) && acceptableDetour(n,linkCost,endCost,toNodeIdx,detourLimit) &&
                    acceptableLocation(toNodeIdx,startCoord,endCoord,endCost,detourLimit)) {
                c1.incCounter();
                TreeNode newPath = new TreeNode(toNodeIdx,n,link,n.cost + linkCost);
                pathsIn.computeIfAbsent(toNodeIdx, k -> new HashSet<>()).add(newPath);
                explore(newPath,endCost,startCoord,endCoord,detourLimit);
            }
        }
    }

    public double getCost(int nodeIndex) {
        return this.data[nodeIndex];
    }

    private void setCost(int nodeIndex, double cost) {
        this.data[nodeIndex] = cost;
    }

    public boolean acceptableLocation(int nodeIdx, Coord startCoord, Coord endCoord, double pathCost, double detourLimit) {
        Coord nodeCoord = this.graph.getNode(nodeIdx).getCoord();
        double origDist = CoordUtils.calcProjectedEuclideanDistance(nodeCoord,startCoord);
        double destDist = CoordUtils.calcProjectedEuclideanDistance(nodeCoord,endCoord);

        return (origDist + destDist) <= (pathCost * detourLimit);

    }

    public boolean acceptableDetour(TreeNode p, double linkCost, double endCost, int toNodeIdx, double detourLimit) {

        double pathCost = p.cost + linkCost;
        if (pathCost > endCost * detourLimit) {
            return false;
        }

        TreeNode currPath = p;
        while (currPath != null) {
            c2.incCounter();
            int nodeIdx = currPath.nodeIdx;

            // Find intersection with shortest path
            int currIdx = toNodeIdx;
            while (currIdx != -1) {
                if (currIdx == nodeIdx) {
                    double shortestDistance = getCost(toNodeIdx) - getCost(nodeIdx);
                    double pathDistance = pathCost - currPath.cost;
                    return pathDistance <= shortestDistance * detourLimit;
                }
                currIdx = getComingFrom(currIdx);
            }
            currPath = currPath.parent;
        }
        throw new RuntimeException("Shouldn't get to the end of this loop!");
    }

    public Set<List<Link>> getAllPaths(int destinationNodeIdx) {
        Set<List<Link>> allPaths = new HashSet<>();

        for (TreeNode p : pathsIn.get(destinationNodeIdx)) {
            List<Link> pathLinks = new ArrayList<>();

            TreeNode curr = p;
            while (curr.link != null) {
                pathLinks.add(curr.link);
                curr = curr.parent;
            }
            Collections.reverse(pathLinks);
            allPaths.add(pathLinks);
        }
        return allPaths;
    }

    public boolean isLoop(TreeNode n, int nodeIdx) {
        TreeNode curr = n;
        while (curr != null) {
            if (curr.nodeIdx == nodeIdx) {
                return true;
            }
            curr = curr.parent;
        }
        return false;
    }

    private static class TreeNode {
        TreeNode parent;
        final Link link;
        final double cost;
        final int nodeIdx;

        public TreeNode(int nodeIdx, TreeNode parent, Link link, double cost) {
            this.parent = parent;
            this.nodeIdx = nodeIdx;
            this.link = link;
            this.cost = cost;
        }
    }

    public int getComingFrom(int nodeIndex) {
        return this.comingFrom[nodeIndex];
    }
}
