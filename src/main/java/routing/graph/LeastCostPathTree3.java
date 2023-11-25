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
 * The implementation does not allocate any memory in the {@link #calculate(int, double, boolean)} method. All required memory is pre-allocated in the constructor. This makes the
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

    public void calculate(int startNode, double startTime, boolean fwd) {
        this.calculate(startNode, startTime, (node, arrTime, cost, distance, depTime) -> false, fwd);
    }

    public void calculate(int startNode, double startTime, StopCriterion stopCriterion, boolean fwd) {

        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFrom, -1);

        setData(startNode, 0, startTime, 0);

        this.pq.clear();
        this.pq.insert(startNode);

        fillTree(startTime, stopCriterion, fwd);
    }

    public void calculate(int startNode1, double cost1, double time1, double dist1,
                           int startNode2, double cost2, double time2, double dist2,
                           double startTime, StopCriterion stopCriterion, boolean fwd) {

        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFrom, -1);

        setData(startNode1,cost1,time1,dist1);
        setData(startNode2,cost2,time2,dist2);

        this.pq.clear();
        if (cost1 < cost2) {
            this.pq.insert(startNode1);
            this.pq.insert(startNode2);
        } else {
            this.pq.insert(startNode2);
            this.pq.insert(startNode1);
        }

        fillTree(startTime, stopCriterion, fwd);
    }

    private void fillTree(double startTime, StopCriterion stopCriterion, boolean fwd) {
        SpeedyGraph.LinkIterator LI = fwd ? this.outLI : this.inLI;
        while (!this.pq.isEmpty()) {
            final int nodeIdx = this.pq.poll();
            OptionalTime currOptionalTime = getTime(nodeIdx);
            double currTime = currOptionalTime.orElseThrow(() -> new RuntimeException("Undefined Time"));
            double currCost = getCost(nodeIdx);
            double currDistance = getDistance(nodeIdx);

            if (stopCriterion.stop(nodeIdx, currTime, currCost, currDistance, startTime)) {
                break;
            }

            LI.reset(nodeIdx);

            while (LI.next()) {
                int linkIdx = LI.getLinkIndex();
                Link link = this.graph.getLink(linkIdx);
                int nextNode = fwd ? LI.getToNodeIndex() : LI.getFromNodeIndex();

                double oldCost = getCost(nextNode);
                double newTime = currTime + this.graph.getLinkTime(linkIdx);
                double newCost = currCost + this.graph.getLinkDisutility(linkIdx);

                if (Double.isFinite(oldCost)) {
                    if (newCost < oldCost) {
                        this.pq.decreaseKey(nextNode, newCost);
                        setData(nextNode, newCost, newTime, currDistance + link.getLength());
                        this.comingFrom[nextNode] = nodeIdx;
                    }
                } else {
                    setData(nextNode, newCost, newTime, currDistance + link.getLength());
                    this.pq.insert(nextNode);
                    this.comingFrom[nextNode] = nodeIdx;
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
