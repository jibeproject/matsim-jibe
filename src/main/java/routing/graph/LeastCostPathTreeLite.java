package routing.graph;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// FASTER VERSION USING PRECALCULATED DISUTILITIES

/**
 * Implements a least-cost-path-tree upon a {@link SpeedyGraph} datastructure. Besides using the more efficient Graph datastructure, it also makes use of a custom priority-queue implementation (NodeMinHeap)
 * which operates directly on the least-cost-path-three data for additional performance gains.
 * <p>
 * In some limited tests, this resulted in a speed-up of at least a factor 2.5 compared to MATSim's default LeastCostPathTree.
 * <p>
 * The implementation does not allocate any memory in the {@link #calculate(int)} method. All required memory is pre-allocated in the constructor. This makes the
 * implementation NOT thread-safe.
 *
 * @author mrieser / Simunto, sponsored by SBB Swiss Federal Railways
 */
public class LeastCostPathTreeLite {

    private final SpeedyGraph graph;
    private final double[] data; // 2 entries per node: cost, distance
    private final int[] comingFrom;
    private final SpeedyGraph.LinkIterator outLI;
    private final SpeedyGraph.LinkIterator inLI;
    private final NodeMinHeap pq;

    public LeastCostPathTreeLite(SpeedyGraph graph) {
        this.graph = graph;
        this.data = new double[graph.nodeCount * 2];
        this.comingFrom = new int[graph.nodeCount];
        this.pq = new NodeMinHeap(graph.nodeCount, this::getCost, this::setCost);
        this.outLI = graph.getOutLinkIterator();
        this.inLI = graph.getInLinkIterator();
    }

    public void calculate(int startNode) {
        this.calculate(startNode, (node, cost, distance) -> false);
    }

    public void calculate(int startNode, StopCriterion stopCriterion) {
        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFrom, -1);

        setData(startNode, 0, 0);

        this.pq.clear();
        this.pq.insert(startNode);

        while (!this.pq.isEmpty()) {
            final int nodeIdx = this.pq.poll();

            double currCost = getCost(nodeIdx);
            double currDistance = getDistance(nodeIdx);

            if (stopCriterion.stop(nodeIdx, currCost, currDistance)) {
                break;
            }

            this.outLI.reset(nodeIdx);
            while (this.outLI.next()) {
                int linkIdx = this.outLI.getLinkIndex();
                Link link = this.graph.getLink(linkIdx);
                int toNode = this.outLI.getToNodeIndex();

                double newCost = currCost + this.graph.getLinkDisutility(linkIdx);

                double oldCost = getCost(toNode);
                if (Double.isFinite(oldCost)) {
                    if (newCost < oldCost) {
                        this.pq.decreaseKey(toNode, newCost);
                        setData(toNode, newCost, currDistance + link.getLength());
                        this.comingFrom[toNode] = nodeIdx;
                    }
                } else {
                    setData(toNode, newCost, currDistance + link.getLength());
                    this.pq.insert(toNode);
                    this.comingFrom[toNode] = nodeIdx;
                }
            }
        }
    }

    public double getCost(int nodeIndex) {
        return this.data[nodeIndex * 2];
    }

    public double getDistance(int nodeIndex) {
        return this.data[nodeIndex * 2 + 1];
    }

    private void setCost(int nodeIndex, double cost) {
        this.data[nodeIndex * 2] = cost;
    }

    private void setData(int nodeIndex, double cost, double distance) {
        int index = nodeIndex * 2;
        this.data[index] = cost;
        this.data[index + 1] = distance;
    }

    public interface StopCriterion {

        boolean stop(int nodeIndex, double travelCost, double distance);
    }

    public static final class TravelDistanceStopCriterion implements StopCriterion {

        private final double limit;

        public TravelDistanceStopCriterion(double limit) {
            this.limit = limit;
        }

        @Override
        public boolean stop(int nodeIndex, double travelCost, double distance) {
            return distance >= this.limit;
        }
    }

}
