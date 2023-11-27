package ch.sbb.matsim.graph;

import routing.TravelAttribute;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Implements a least-cost-path-tree upon a {@link Graph} datastructure.
 * Besides using the more efficient Graph datastructure, it also makes
 * use of a custom priority-queue implementation (NodeMinHeap) which
 * operates directly on the least-cost-path-three data for additional
 * performance gains.
 *
 * In some limited tests, this resulted in a speed-up of at least a
 * factor 2.5 compared to MATSim's default LeastCostPathTree.
 *
 * The implementation does not allocate any memory in the
 * {@link #calculate(int, double, Person, Vehicle)} method. All required
 * memory is pre-allocated in the constructor. This makes the implementation
 * NOT thread-safe.
 */
public class LeastCostPathTree2 {

    private final Graph graph;
    private final TravelTime tt;
    private final TravelDisutility td;
    private final TravelAttribute[] ta;
    private final int attributeCount;
    private final int entriesPerNode;
    private final double[] data; // minimum 3 entries per node: time, cost, distance
    private final int[] linksUsed;
    private final int[] comingFromNode;
    private final int[] comingFromLink;
    private final Graph.LinkIterator outLI;
    private final Graph.LinkIterator inLI;
    private final NodeMinHeap pq;

    public LeastCostPathTree2(Graph graph, TravelTime tt, TravelDisutility td, TravelAttribute[] ta) {
        this.graph = graph;
        this.tt = tt;
        this.td = td;
        this.ta = ta;
        this.attributeCount = ta != null ? ta.length : 0;
        this.entriesPerNode = 3 + attributeCount;
        this.data = new double[graph.nodeCount * entriesPerNode];
        this.linksUsed = new int[graph.nodeCount];
        this.comingFromNode = new int[graph.nodeCount];
        this.comingFromLink = new int[graph.nodeCount];
        this.pq = new NodeMinHeap();
        this.outLI = graph.getOutLinkIterator();
        this.inLI = graph.getInLinkIterator();
    }

    public void calculate(int startNode, double startTime, Person person, Vehicle vehicle) {
        this.calculate(startNode, startTime, person, vehicle, (node, arrTime, cost, distance, depTime) -> false);
    }

    public void calculate(int startNode, double startTime, Person person, Vehicle vehicle, StopCriterion stopCriterion) {
        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFromNode, -1);

        setData(startNode, 0., startTime, 0., 0);
        for(int i = 0 ; i < attributeCount ; i++) {
            setAttribute(startNode, i, 0.);
        }

        this.pq.clear();
        this.pq.insert(startNode);

        while (!pq.isEmpty()) {
            final int nodeIdx = pq.poll();
            double currTime = getTime(nodeIdx);
            double currCost = getCost(nodeIdx);
            double currDistance = getDistance(nodeIdx);
            int currLinksUsed = getLinksUsed(nodeIdx);
            double[] currAttr = getAttributes(nodeIdx);

            if (stopCriterion.stop(nodeIdx, currTime, currCost, currDistance, startTime)) {
                break;
            }

            outLI.reset(nodeIdx);
            while (outLI.next()) {
                int linkIdx = outLI.getLinkIndex();
                Link link = this.graph.getLink(linkIdx);
                int toNode = outLI.getToNodeIndex();

                double newTime = currTime + this.tt.getLinkTravelTime(link, currTime, person, vehicle);
                double newCost = currCost + this.td.getLinkTravelDisutility(link, currTime, person, vehicle);
                int newLinksUsed = currLinksUsed + 1;

                double oldCost = getCost(toNode);
                if (Double.isFinite(oldCost)) {
                    if (newCost < oldCost) {
                        pq.decreaseKey(toNode, newCost);
                        setData(toNode, newCost, newTime, currDistance + link.getLength(), newLinksUsed);
                        for(int i = 0 ; i < attributeCount ; i++) {
                            double newAttr = currAttr[i] + this.ta[i].getTravelAttribute(link,td,tt);
                            setAttribute(toNode, i, newAttr);
                        }
                        this.comingFromNode[toNode] = nodeIdx;
                        this.comingFromLink[toNode] = linkIdx;
                    }
                } else {
                    setData(toNode, newCost, newTime, currDistance + link.getLength(), newLinksUsed);
                    for(int i = 0 ; i < attributeCount ; i++) {
                        double newAttr = currAttr[i] + this.ta[i].getTravelAttribute(link,td,tt);
                        setAttribute(toNode, i, newAttr);
                    }
                    pq.insert(toNode);
                    this.comingFromNode[toNode] = nodeIdx;
                    this.comingFromLink[toNode] = linkIdx;
                }
            }
        }
    }

    public int getLinksUsed(int nodeIndex) { return this.linksUsed[nodeIndex]; }

    public double getCost(int nodeIndex) {
        return this.data[nodeIndex * entriesPerNode];
    }

    public double getTime(int nodeIndex) {
        return this.data[nodeIndex * entriesPerNode + 1];
    }

    public double getDistance(int nodeIndex) {
        return this.data[nodeIndex * entriesPerNode + 2];
    }

    public double getAttribute(int nodeIndex, int attrIndex) { return this.data[nodeIndex * entriesPerNode + 3 + attrIndex]; }

    public double[] getAttributes(int nodeIndex) {
        double[] attributes = new double[attributeCount];
        for(int i = 0 ; i < attributeCount ; i++) {
            attributes[i] = this.data[nodeIndex * entriesPerNode + 3 + i];
        }
        return attributes;
    }

    public int[] getLinkArray(int nodeIndex) {
        int linksUsed = getLinksUsed(nodeIndex);
        int[] linkArray = new int[linksUsed];
        int currNode = nodeIndex;
        for(int i = linksUsed ; i > 0 ; i--) {
            int linkIdx = getComingFromLink(currNode);
            linkArray[i-1] = (int) this.graph.getLink(linkIdx).getAttributes().getAttribute("edgeID");
            currNode = getComingFromNode(currNode);
        }
        return linkArray;
    }

    private void setAttribute(int nodeIndex, int attrIndex, double attr) { this.data[nodeIndex * entriesPerNode + 3 + attrIndex] = attr; }

    private void setCost(int nodeIndex, double cost) {
        this.data[nodeIndex * entriesPerNode] = cost;
    }

    private void setData(int nodeIndex, double cost, double time, double distance, int linksUsed) {
        int index = nodeIndex * entriesPerNode;
        this.data[index] = cost;
        this.data[index + 1] = time;
        this.data[index + 2] = distance;
        this.linksUsed[nodeIndex] = linksUsed;
    }

    public int getComingFromNode(int nodeIndex) {
        return this.comingFromNode[nodeIndex];
    }

    public int getComingFromLink(int nodeIndex) {
        return this.comingFromLink[nodeIndex];
    }

    private class NodeMinHeap {

        private final int[] heap;
        private int size = 0;

        NodeMinHeap() {
            this.heap = new int[graph.nodeCount]; // worst case: every node is part of the heap
        }

        void insert(int node) {
            int i = this.size;
            heap[i] = node;
            this.size++;

            int parent = parent(i);

            while (parent != i && getCost(heap[i]) < getCost(heap[parent])) {
                swap(i, parent);
                i = parent;
                parent = parent(i);
            }
        }

        void decreaseKey(int node, double cost) {
            int i;
            for (i = 0; i < size; i++) {
                if (this.heap[i] == node) {
                    break;
                }
            }
            if (getCost(heap[i]) < cost) {
                throw new IllegalArgumentException("existing cost is already smaller than new cost.");
            }

            setCost(node, cost);
            int parent = parent(i);

            // sift up
            while (i > 0 && getCost(heap[parent]) > getCost(heap[i])) {
                swap(i, parent);
                i = parent;
                parent = parent(parent);
            }
        }

        int poll() {
            if (this.size == 0) {
                throw new NoSuchElementException("heap is empty");
            }
            if (this.size == 1) {
                this.size--;
                return this.heap[0];
            }

            int root = this.heap[0];

            // remove the last item, set it as new root
            int lastNode = this.heap[this.size - 1];
            this.size--;
            this.heap[0] = lastNode;

            // sift down
            minHeapify(0);

            return root;
        }

        int peek() {
            if (this.size == 0) {
                throw new NoSuchElementException("heap is empty");
            }
            return this.heap[0];
        }

        int size() {
            return this.size;
        }

        boolean isEmpty() {
            return this.size == 0;
        }

        void clear() {
            this.size = 0;
        }

        private void minHeapify(int i) {
            int left = left(i);
            int right = right(i);
            int smallest = i;

            if (left <= (size - 1) && getCost(heap[left]) < getCost(heap[i])) {
                smallest = left;
            }
            if (right <= (size - 1) && getCost(heap[right]) < getCost(heap[smallest])) {
                smallest = right;
            }
            if (smallest != i) {
                swap(i, smallest);
                minHeapify(smallest);
            }
        }

        private int right(int i) {
            return 2 * i + 2;
        }

        private int left(int i) {
            return 2 * i + 1;
        }

        private int parent(int i) {
            return (i - 1) / 2;
        }

        private void swap(int i, int parent) {
            int tmp = this.heap[parent];
            this.heap[parent] = this.heap[i];
            this.heap[i] = tmp;
        }
    }

    public interface StopCriterion {
        boolean stop(int nodeIndex, double arrivalTime, double travelCost, double distance, double departureTime);
    }

    public static final class TravelTimeStopCriterion implements StopCriterion {

        private final double limit;

        public TravelTimeStopCriterion(double limit) {
            this.limit = limit;
        }

        @Override
        public boolean stop(int nodeIndex, double arrivalTime, double travelCost, double distance, double departureTime) {
            return Math.abs(arrivalTime - departureTime) >= this.limit; // use Math.abs() so it also works in backwards search
        }
    }

    public static final class TravelDistanceStopCriterion implements StopCriterion {

        private final double limit;

        public TravelDistanceStopCriterion(double limit) {
            this.limit = limit;
        }

        @Override
        public boolean stop(int nodeIndex, double arrivalTime, double travelCost, double distance, double departureTime) {
            return distance >= this.limit;
        }
    }

}
