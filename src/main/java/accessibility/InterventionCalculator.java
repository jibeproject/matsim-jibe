/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility;

import accessibility.decay.DecayFunction;
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
import routing.graph.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


// Based on the skim matrix calculations from the MATSim SBB Extensions
public final class InterventionCalculator {

    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));
    final SpeedyGraph routingGraph;
    private final DecayFunction decayFunction;

    public InterventionCalculator(Network routingNetwork, TravelTime travelTime, TravelDisutility travelDisutility,
                                  Vehicle vehicle, DecayFunction decayFunction) {
        this.routingGraph = new SpeedyGraph(routingNetwork,travelTime,travelDisutility,PERSON,vehicle);
        this.decayFunction = decayFunction;
    }


    public Map<Id<Node>,Double> calculate(Set<Id<Node>> startNodes, Map<Id<Node>,Double> endNodes) {

        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);

        // prepare calculation
        ConcurrentHashMap<Id<Node>,Double> accessibilityResults = new ConcurrentHashMap<>(startNodes.size());

        // do calculation
        ConcurrentLinkedQueue<Id<Node>> startNodesQueue = new ConcurrentLinkedQueue<>(startNodes);

        Counter counter = new Counter("Calculating accessibility node ", " / " + startNodes.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            NodeWorker worker = new NodeWorker(startNodesQueue, endNodes, accessibilityResults, counter);
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

    public Map<Id<Node>,Double> calculateSingle(Set<Id<Node>> startNodes, Id<Node> newNode, Double wt) {

        PathTree lcpTree = new LcpTree2Way(routingGraph);
        StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

        lcpTree.calculate(newNode.index(),0.,stopCriterion);

        IdMap<Node,Double> result = new IdMap<>(Node.class);
        for(Id<Node> node : startNodes) {
            int toNodeIndex = node.index();
            double dist = lcpTree.getDistance(toNodeIndex);
            double time = lcpTree.getTime(toNodeIndex).orElse(Double.POSITIVE_INFINITY);
            if(decayFunction.beyondCutoff(dist,time)) {
                result.put(node,0.);
            } else {
                double cost = lcpTree.getCost(toNodeIndex);
                result.put(node,decayFunction.getDecay(cost) * wt);
            }

        }
        return result;
    }

    private class NodeWorker implements Runnable {
        private final ConcurrentLinkedQueue<Id<Node>> startNodes;
        private final Map<Id<Node>,Double> endNodes;
        private final ConcurrentHashMap<Id<Node>,Double> accessibilityData;
        private final Counter counter;

        NodeWorker(ConcurrentLinkedQueue<Id<Node>> startNodes, Map<Id<Node>,Double> endNodes,
                   ConcurrentHashMap<Id<Node>,Double> results, Counter counter) {
            this.startNodes = startNodes;
            this.endNodes = endNodes;
            this.accessibilityData = results;
            this.counter = counter;
        }

        public void run() {
            PathTree lcpTree = new LcpTree2Way(routingGraph);
            StopCriterion stopCriterion = decayFunction.getTreeStopCriterion();

            while (true) {
                Id<Node> fromNodeId = this.startNodes.poll();
                if (fromNodeId == null) {
                    return;
                }

                this.counter.incCounter();
                lcpTree.calculate(fromNodeId.index(),0.,stopCriterion);

                double accessibility = 0.;

                for (Map.Entry<Id<Node>, Double> e : this.endNodes.entrySet()) {
                    int toNodeIndex = e.getKey().index();
                    double dist = lcpTree.getDistance(toNodeIndex);
                    double time = lcpTree.getTime(toNodeIndex).orElse(Double.POSITIVE_INFINITY);
                    if(!decayFunction.beyondCutoff(dist,time)) {
                        double cost = lcpTree.getCost(toNodeIndex);
                        accessibility += decayFunction.getDecay(cost) * e.getValue();
                    }
                }
                this.accessibilityData.put(fromNodeId,accessibility);
            }
        }
    }
}
