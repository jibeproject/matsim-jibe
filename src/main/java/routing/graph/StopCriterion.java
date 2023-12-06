package routing.graph;

public interface StopCriterion {
    boolean stop(int nodeIndex, double arrivalTime, double travelCost, double distance, double departureTime);
}
