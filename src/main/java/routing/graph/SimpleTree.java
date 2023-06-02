package routing.graph;

import java.util.*;

/**
 * Simple distance tree based on LeastCostPathTree
 */
public class SimpleTree {

    private final SpeedyGraph graph;
    private final double[] data;
    private final int[] comingFrom;
    private final int[] comingFromLink;

    private final SpeedyGraph.LinkIterator outLI;
    private final SpeedyGraph.LinkIterator inLI;
    private final NodeMinHeap pq;

    public SimpleTree(SpeedyGraph graph) {
        this.graph = graph;
        this.data = new double[graph.nodeCount];
        this.comingFrom = new int[graph.nodeCount];
        this.comingFromLink = new int[graph.nodeCount];
        this.pq = new NodeMinHeap(graph.nodeCount, this::getCost, this::setCost);
        this.outLI = graph.getOutLinkIterator();
        this.inLI = graph.getInLinkIterator();
    }

    public void calculate(int startNodeIdx, int endNodeIdx,int calcNodeIdx, double detourLimit,boolean fwd) {

        SpeedyGraph.LinkIterator LI = fwd ? this.outLI : this.inLI;

        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFrom, -1);
        Arrays.fill(this.comingFromLink,-1);

        // Root node (i.e. the node to calculate from)
        int rootNode;
        if(calcNodeIdx >= 0) {
            assert detourLimit < 0;
            rootNode = calcNodeIdx;
        } else {
            rootNode = fwd ? startNodeIdx : endNodeIdx;
        }

        int finalNode = fwd ? endNodeIdx : startNodeIdx;

        setCost(rootNode,0.);

        this.pq.clear();
        this.pq.insert(rootNode);

        boolean startNodeReached = false;
        boolean endNodeReached = false;

        while (!this.pq.isEmpty()) {
            final int nodeIdx = this.pq.poll();
            double currCost = getCost(nodeIdx);


            if(calcNodeIdx >= 0) {
                if(nodeIdx == startNodeIdx) {
                    if(endNodeReached) {
                        break;
                    } else {
                        startNodeReached = true;
                    }
                } else if(nodeIdx == endNodeIdx) {
                    if(startNodeReached) {
                        break;
                    } else {
                        endNodeReached = true;
                    }
                }
            } else if (currCost > detourLimit * getCost(finalNode)) {
                break;
            }


            LI.reset(nodeIdx);
            while (LI.next()) {
                int linkIdx = LI.getLinkIndex();

                int nextNode = fwd ? LI.getToNodeIndex() : LI.getFromNodeIndex();

                double oldCost = getCost(nextNode);
                double newCost = currCost + this.graph.getLinkDisutility(linkIdx); // link.getLength();

                if (Double.isFinite(oldCost)) {
                    if (newCost < oldCost) {
                        this.pq.decreaseKey(nextNode, newCost);
                        setCost(nextNode, newCost);
                        this.comingFrom[nextNode] = nodeIdx;
                        this.comingFromLink[nextNode] = linkIdx;
                    }
                } else {
                    setCost(nextNode, newCost);
                    this.pq.insert(nextNode);
                    this.comingFrom[nextNode] = nodeIdx;
                    this.comingFromLink[nextNode] = linkIdx;
                }
            }
        }
    }

    public double getCost(int nodeIndex) {
        return this.data[nodeIndex];
    }

    private void setCost(int nodeIndex, double cost) {
        this.data[nodeIndex] = cost;
    }

    public int getComingFrom(int nodeIndex) {
        return this.comingFrom[nodeIndex];
    }

    public int getComingFromLink(int nodeIdx) { return this.comingFromLink[nodeIdx]; }
}
