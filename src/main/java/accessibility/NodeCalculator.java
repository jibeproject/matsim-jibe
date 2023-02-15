/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility;

import accessibility.impedance.DecayFunction;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import resources.Properties;
import resources.Resources;
import routing.graph.LeastCostPathTree3;
import routing.graph.SpeedyGraph;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


// Based on the skim matrix calculations from the MATSim SBB Extensions
public final class NodeCalculator {

    private NodeCalculator() {
    }

    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public static <T> IdMap<Node,Double> calculate(Network routingNetwork, Set<Node> origins,
                                                   Map<T, List<Node>> destinationNodes, Map<T, Double> destinationWeights,
                                                   TravelTime travelTime, TravelDisutility travelDisutility,
                                                   Vehicle vehicle, DecayFunction decayFunction) {

        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        SpeedyGraph routingGraph = new SpeedyGraph(routingNetwork,travelTime,travelDisutility,PERSON,vehicle);

        // prepare calculation
        ConcurrentHashMap<Id<Node>,Double> accessibilityData = new ConcurrentHashMap<>(origins.size());

        // do calculation
        ConcurrentLinkedQueue<Node> originNodes = new ConcurrentLinkedQueue<>(origins);

        Counter counter = new Counter("Calculating accessibility node ", " / " + origins.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            NodeWorker<T> worker = new NodeWorker<>(originNodes, destinationWeights, routingGraph,
                    destinationNodes, accessibilityData, decayFunction, counter);
            threads[i] = new Thread(worker, "Accessibility-" + i);
            threads[i].start();
        }

        // wait until all threads have finished
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        IdMap<Node,Double> results = new IdMap<>(Node.class);
        results.putAll(accessibilityData);

        return results;
    }

    private static class NodeWorker<T> implements Runnable {
        private final ConcurrentLinkedQueue<Node> originNodes;
        private final Map<T, Double> destinations;
        private final SpeedyGraph graph;
        private final Map<T, List<Node>> destinationNodes;
        private final ConcurrentHashMap<Id<Node>,Double> accessibilityData;
        private final DecayFunction decayFunction;
        private final Counter counter;

        NodeWorker(ConcurrentLinkedQueue<Node> originNodes, Map<T, Double> destinationWeights, SpeedyGraph graph,
                   Map<T, List<Node>> destinationNodes, ConcurrentHashMap<Id<Node>,Double> accessibilityData,
                   DecayFunction decayFunction, Counter counter) {
            this.originNodes = originNodes;
            this.destinations = destinationWeights;
            this.graph = graph;
            this.destinationNodes = destinationNodes;
            this.accessibilityData = accessibilityData;
            this.decayFunction = decayFunction;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree3 lcpTree = new LeastCostPathTree3(this.graph);
            LeastCostPathTree3.StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

            while (true) {
                Node fromNode = this.originNodes.poll();
                if (fromNode == null) {
                    return;
                }

                this.counter.incCounter();
                lcpTree.calculate(fromNode.getId().index(),0,stopCriterion);

                double accessibility = 0.;

                for (Map.Entry<T, Double> destination : this.destinations.entrySet()) {

                    double cost = Double.MAX_VALUE;

                    for (Node toNode : this.destinationNodes.get(destination.getKey())) {
                        int toNodeIndex = toNode.getId().index();
                        double nodeDist = lcpTree.getDistance(toNodeIndex);
                        double nodeTime = lcpTree.getTime(toNodeIndex).orElse(Double.POSITIVE_INFINITY);
                        if(!decayFunction.withinCutoff(nodeDist,nodeTime)) {
                            continue;
                        }
                        double nodeCost = lcpTree.getCost(toNodeIndex);
                        if (nodeCost < cost) {
                            cost = nodeCost;
                        }
                    }
                    if(cost != Double.MAX_VALUE) {
                        double wt = destination.getValue();
                        accessibility += decayFunction.getDecay(cost) * wt;
                    }
                }
                this.accessibilityData.put(fromNode.getId(),accessibility);
            }
        }
    }
}
