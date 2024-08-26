package routing.graph;

import org.matsim.core.utils.misc.OptionalTime;

public class LcpTree2Way implements PathTree {

    LcpTree1Way lcpTreeFwd;
    LcpTree1Way lcpTreeRev;

    public LcpTree2Way(SpeedyGraph graph) {
        lcpTreeFwd = new LcpTree1Way(graph,true);
        lcpTreeRev = new LcpTree1Way(graph,false);
    }

    @Override
    public void calculate(int startNode, double startTime) {
        lcpTreeFwd.calculate(startNode,startTime);
        lcpTreeRev.calculate(startNode,startTime);
    }

    @Override
    public void calculate(int startNode, double startTime, StopCriterion stopCriterion) {
        lcpTreeFwd.calculate(startNode,startTime,stopCriterion);
        lcpTreeRev.calculate(startNode,startTime,stopCriterion);
    }

    @Override
    public void calculate(int startNode1, double cost1, double time1, double dist1,
                          int startNode2, double cost2, double time2, double dist2,
                          double startTime, StopCriterion stopCriterion) {
        lcpTreeFwd.calculate(startNode1,cost1,time1,dist1,startNode2,cost2,time2,dist2,startTime,stopCriterion);
        lcpTreeRev.calculate(startNode1,cost1,time1,dist1,startNode2,cost2,time2,dist2,startTime,stopCriterion);
    }

    @Override
    public double getCost(int nodeIndex) {
        return (lcpTreeFwd.getCost(nodeIndex) + lcpTreeRev.getCost(nodeIndex))/2;
    }

    @Override
    public double getDistance(int nodeIndex) {
        return (lcpTreeFwd.getDistance(nodeIndex) + lcpTreeFwd.getDistance(nodeIndex))/2;
    }

    @Override
    public OptionalTime getTime(int nodeIndex) {
        double timeFwd = lcpTreeFwd.getTime(nodeIndex).orElse(Double.POSITIVE_INFINITY);
        double timeRev = lcpTreeRev.getTime(nodeIndex).orElse(Double.POSITIVE_INFINITY);
        double time = timeFwd + timeRev;
        if (Double.isInfinite(time)) {
            return OptionalTime.undefined();
        }
        return OptionalTime.defined(time/2);
    }
}
