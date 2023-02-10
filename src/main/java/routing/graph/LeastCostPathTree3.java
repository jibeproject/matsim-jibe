package routing.graph;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.misc.OptionalTime;

import java.util.Arrays;

/**
 * Implements a least-cost-path-tree upon a {@link org.matsim.core.router.speedy.SpeedyGraph} datastructure. Besides using the more efficient Graph datastructure, it also makes use of a custom priority-queue implementation (NodeMinHeap)
 * which operates directly on the least-cost-path-three data for additional performance gains.
 * <p>
 * In some limited tests, this resulted in a speed-up of at least a factor 2.5 compared to MATSim's default LeastCostPathTree.
 * <p>
 * The implementation does not allocate any memory in the {@link #calculate(int, double)} method. All required memory is pre-allocated in the constructor. This makes the
 * implementation NOT thread-safe.
 *
 * @author mrieser / Simunto, sponsored by SBB Swiss Federal Railways
 */
public class LeastCostPathTree3 {

    private final SpeedyGraph graph;
    private final double[] data; // 3 entries per node: time, cost, distance
    private final int[] comingFrom;
    private final SpeedyGraph.LinkIterator outLI;
    private final SpeedyGraph.LinkIterator inLI;
    private final NodeMinHeap pq;

    public LeastCostPathTree3(SpeedyGraph graph) {
        this.graph = graph;
        this.data = new double[graph.nodeCount * 3];
        this.comingFrom = new int[graph.nodeCount];
        this.pq = new NodeMinHeap(graph.nodeCount, this::getCost, this::setCost);
        this.outLI = graph.getOutLinkIterator();
        this.inLI = graph.getInLinkIterator();
    }

    public void calculate(int startNode, double startTime) {
        this.calculate(startNode, startTime, (node, arrTime, cost, distance, depTime) -> false);
    }

    public void calculate(int startNode, double startTime, StopCriterion stopCriterion) {
        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFrom, -1);

        setData(startNode, 0, startTime, 0);

        this.pq.clear();
        this.pq.insert(startNode);

        while (!this.pq.isEmpty()) {
            final int nodeIdx = this.pq.poll();
            OptionalTime currOptionalTime = getTime(nodeIdx);
            double currTime = currOptionalTime.orElseThrow(() -> new RuntimeException("Undefined Time"));
            double currCost = getCost(nodeIdx);
            double currDistance = getDistance(nodeIdx);

            if (stopCriterion.stop(nodeIdx, currTime, currCost, currDistance, startTime)) {
                break;
            }

            this.outLI.reset(nodeIdx);
            while (this.outLI.next()) {
                int linkIdx = this.outLI.getLinkIndex();
                Link link = this.graph.getLink(linkIdx);
                int toNode = this.outLI.getToNodeIndex();

                double newTime = currTime + this.graph.getLinkTime(linkIdx);
                double newCost = currCost + this.graph.getLinkDisutility(linkIdx);

                double oldCost = getCost(toNode);
                if (Double.isFinite(oldCost)) {
                    if (newCost < oldCost) {
                        this.pq.decreaseKey(toNode, newCost);
                        setData(toNode, newCost, newTime, currDistance + link.getLength());
                        this.comingFrom[toNode] = nodeIdx;
                    }
                } else {
                    setData(toNode, newCost, newTime, currDistance + link.getLength());
                    this.pq.insert(toNode);
                    this.comingFrom[toNode] = nodeIdx;
                }
            }
        }
    }

    public double getCost(int nodeIndex) {
        return this.data[nodeIndex * 3];
    }

    public OptionalTime getTime(int nodeIndex) {
        double time = this.data[nodeIndex * 3 + 1];
        if (Double.isInfinite(time)) {
            return OptionalTime.undefined();
        }
        return OptionalTime.defined(time);
    }

    public double getDistance(int nodeIndex) {
        return this.data[nodeIndex * 3 + 2];
    }

    private void setCost(int nodeIndex, double cost) {
        this.data[nodeIndex * 3] = cost;
    }

    private void setData(int nodeIndex, double cost, double time, double distance) {
        int index = nodeIndex * 3;
        this.data[index] = cost;
        this.data[index + 1] = time;
        this.data[index + 2] = distance;
    }

    public int getComingFrom(int nodeIndex) {
        return this.comingFrom[nodeIndex];
    }

    public interface StopCriterion {

        boolean stop(int nodeIndex, double arrivalTime, double travelCost, double distance, double departureTime);
    }
}
