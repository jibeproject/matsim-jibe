package routing.graph;

import org.matsim.core.utils.misc.OptionalTime;

public interface PathTree {

    void calculate(int startNode, double startTime);

    void calculate(int startNode, double startTime, StopCriterion stopCriterion);

    void calculate(int startNode1, double cost1, double time1, double dist1,
                   int startNode2, double cost2, double time2, double dist2,
                   double startTime, StopCriterion stopCriterion);

    double getCost(int nodeIndex);
    double getDistance(int nodeIndex);
    OptionalTime getTime(int nodeIndex);

}

