package accessibility.impedance;

import routing.graph.LeastCostPathTree3;

public class DecayFunction {

    final private double cutoffTime;
    final private double cutoffDist;
    final private LeastCostPathTree3.StopCriterion stopCriterion;

    public DecayFunction(double cutoffTime, double cutoffDist) {
        this.cutoffTime = Double.isNaN(cutoffTime) ? Double.MAX_VALUE : cutoffTime;
        this.cutoffDist = Double.isNaN(cutoffDist) ? Double.MAX_VALUE : cutoffDist;

        // Set stop criterion
        if(Double.isNaN(cutoffDist) && Double.isNaN(cutoffTime)) {
            stopCriterion = (node, arrTime, cost, distance, depTime) -> false;
        } else if (Double.isNaN(cutoffDist)) {
            stopCriterion = (node, arrTime, cost, distance, depTime) -> Math.abs(arrTime - depTime) >= cutoffTime;
        } else if (Double.isNaN(cutoffTime)) {
            stopCriterion = (node, arrTime, cost, distance, depTime) -> distance >= cutoffDist;
        } else {
            stopCriterion = (node, arrTime, cost, distance, depTime) -> Math.abs(arrTime - depTime) >= cutoffTime || distance >= cutoffDist;
        }
    }

    public double getDecay(double cost) {
        throw new RuntimeException("Must specify decay function!");
    }

    public boolean withinCutoff(double distance, double time) {
        return distance <= cutoffDist && time <= cutoffTime;
    }

    public LeastCostPathTree3.StopCriterion getTreeStopCriterion() {
        return stopCriterion;
    }
}

