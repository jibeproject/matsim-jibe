/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility;

import accessibility.decay.DecayFunction;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import routing.graph.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


public final class InterventionCalculator extends NodeCalculator {

    public InterventionCalculator(Network routingNetwork, TravelTime travelTime, TravelDisutility travelDisutility,
                                  Vehicle vehicle, DecayFunction decayFunction) {
        super(routingNetwork, travelTime, travelDisutility, vehicle, decayFunction);
    }


    public Map<Id<Node>,double[]> calculateReverse(List<Id<Node>> startNodes, double[] startWeights, Set<Id<Node>> endNodes) {

        PathTree lcpTree = new LcpTree2Way(routingGraph);
        StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

        IdMap<Node,double[]> results = new IdMap<>(Node.class);
        for(Id<Node> nodeId : endNodes) {
            results.put(nodeId,new double[startNodes.size()]);
        }

        for(int i = 0 ; i < startNodes.size() ; i++) {
            lcpTree.calculate(startNodes.get(i).index(),0.,stopCriterion);
            double wt = startWeights[i];
            for(Id<Node> node : endNodes) {
                int toNodeIndex = node.index();
                double dist = lcpTree.getDistance(toNodeIndex);
                double time = lcpTree.getTime(toNodeIndex).orElse(Double.POSITIVE_INFINITY);
                if(decayFunction.beyondCutoff(dist,time)) {
                    results.get(node)[i] = 0;
                } else {
                    double cost = lcpTree.getCost(toNodeIndex);
                    results.get(node)[i] = decayFunction.getDecay(cost) * wt;
                }
            }
        }

        return results;
    }

    public Map<Id<Node>,double[]> calculateDemand(Set<Id<Node>> testNodes, Map<Id<Node>,Double> population, Map<Id<Node>,double[]> accessibility, double[] newWeights) {

        // prepare calculation
        ConcurrentHashMap<Id<Node>,double[]> results = new ConcurrentHashMap<>(testNodes.size());

        // do calculation
        ConcurrentLinkedQueue<Id<Node>> testNodesQueue = new ConcurrentLinkedQueue<>(testNodes);

        Counter counter = new Counter("Calculating accessibility node ", " / " + testNodes.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            NodeWorker worker = new NodeWorker(testNodesQueue, population, accessibility, newWeights, routingGraph, decayFunction, results, counter);
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

        return Collections.unmodifiableMap(new HashMap<>(results));
    }

    private static class NodeWorker implements Runnable {
        private final ConcurrentLinkedQueue<Id<Node>> testNodes;
        Map<Id<Node>,Double> population;
        private final Map<Id<Node>,double[]> accessibility;
        private final double[] newWeights;
        private final int destTypeCount;
        private final SpeedyGraph graph;
        private final ConcurrentHashMap<Id<Node>,double[]> accessibilityData;
        private final DecayFunction decayFunction;
        private final Counter counter;

        NodeWorker(ConcurrentLinkedQueue<Id<Node>> testNodes,
                   Map<Id<Node>,Double> population,
                   Map<Id<Node>,double[]> accessibility,
                   double[] newWeights,
                   SpeedyGraph graph, DecayFunction decayFunction,
                   ConcurrentHashMap<Id<Node>,double[]> results, Counter counter) {
            this.testNodes = testNodes;
            this.population = population;
            this.accessibility = accessibility;
            this.newWeights = newWeights;
            this.graph = graph;
            this.destTypeCount = accessibility.values().iterator().next().length;
            this.decayFunction = decayFunction;
            this.accessibilityData = results;
            this.counter = counter;
        }

        public void run() {
            PathTree lcpTree = new LcpTree2Way(graph);
            StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

            while (true) {
                Id<Node> testNodeId = this.testNodes.poll();
                if (testNodeId == null) {
                    return;
                }

                this.counter.incCounter();
                lcpTree.calculate(testNodeId.index(),0.,stopCriterion);

                double[] demand = new double[destTypeCount];
                for (Map.Entry<Id<Node>, double[]> e : this.accessibility.entrySet()) {
                    Id<Node> nodeId = e.getKey();
                    int nodeIdx = nodeId.index();
                    double[] access = e.getValue();
                    double dist = lcpTree.getDistance(nodeIdx);
                    double time = lcpTree.getTime(nodeIdx).orElse(Double.POSITIVE_INFINITY);
                    if(!decayFunction.beyondCutoff(dist,time)) {
                        double decay = decayFunction.getDecay(lcpTree.getCost(nodeIdx));
                        for(int i = 0 ; i < destTypeCount ; i++) {
                            double newAccess = newWeights[i] * decay;
                            demand[i] += population.get(nodeId) * newAccess / (newAccess + access[i]);
                        }
                    }
                }
                this.accessibilityData.put(testNodeId,demand);
            }
        }
    }
}
