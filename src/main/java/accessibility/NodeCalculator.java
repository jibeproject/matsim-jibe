/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility;

import accessibility.decay.DecayFunction;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


// Based on the skim matrix calculations from the MATSim SBB Extensions
public final class NodeCalculator {

    private NodeCalculator() {
    }

    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public static Map<Id<Node>,Double> calculate(Network routingNetwork, Set<Id<Node>> origins,
                                                   Map<String, IdSet<Node>> destinationNodes, Map<String, Double> destinationWeights,
                                                   TravelTime travelTime, TravelDisutility travelDisutility,
                                                   Vehicle vehicle, DecayFunction decayFunction) {

        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        SpeedyGraph routingGraph = new SpeedyGraph(routingNetwork,travelTime,travelDisutility,PERSON,vehicle);

        // prepare calculation
        ConcurrentHashMap<Id<Node>,Double> accessibilityResults = new ConcurrentHashMap<>(origins.size());

        // do calculation
        ConcurrentLinkedQueue<Id<Node>> originNodes = new ConcurrentLinkedQueue<>(origins);

        Counter counter = new Counter("Calculating accessibility node ", " / " + origins.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            NodeWorker worker = new NodeWorker(originNodes, destinationWeights, destinationNodes,
                    routingGraph, accessibilityResults, decayFunction, counter);
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

        return Collections.unmodifiableMap(new HashMap<>(accessibilityResults));
    }

    private static class NodeWorker implements Runnable {
        private final ConcurrentLinkedQueue<Id<Node>> originNodes;
        private final Map<String, Double> destinationWeights;
        private final Map<String, IdSet<Node>> destinationNodes;
        private final SpeedyGraph graph;
        private final ConcurrentHashMap<Id<Node>,Double> accessibilityData;
        private final DecayFunction decayFunction;
        private final Counter counter;

        NodeWorker(ConcurrentLinkedQueue<Id<Node>> originNodes, Map<String, Double> destinationWeights, Map<String, IdSet<Node>> destinationNodes,
                   SpeedyGraph graph, ConcurrentHashMap<Id<Node>,Double> results,
                   DecayFunction decayFunction, Counter counter) {
            this.originNodes = originNodes;
            this.destinationWeights = destinationWeights;
            this.destinationNodes = destinationNodes;
            this.graph = graph;
            this.accessibilityData = results;
            this.decayFunction = decayFunction;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree3 lcpTree = new LeastCostPathTree3(this.graph);
            LeastCostPathTree3.StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

            while (true) {
                Id<Node> fromNodeId = this.originNodes.poll();
                if (fromNodeId == null) {
                    return;
                }

                this.counter.incCounter();
                lcpTree.calculate(fromNodeId.index(),0.,stopCriterion);

                double accessibility = 0.;

                for (Map.Entry<String, Double> destination : this.destinationWeights.entrySet()) {

                    double cost = Double.MAX_VALUE;

                    for (Id<Node> toNodeId : this.destinationNodes.get(destination.getKey())) {
                        int toNodeIndex = toNodeId.index();
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
                this.accessibilityData.put(fromNodeId,accessibility);
            }
        }
    }
}
