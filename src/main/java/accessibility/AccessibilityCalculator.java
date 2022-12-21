/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility;

import ch.sbb.matsim.analysis.Impedance;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import routing.graph.LeastCostPathTreeLite;
import routing.graph.SpeedyGraph;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Calculates zone-to-zone matrices containing a number of performance indicators related to modes routed on a network.
 *
 * Inspired by <a href="https://github.com/moeckel/silo/blob/siloMatsim/silo/src/main/java/edu/umd/ncsg/transportModel/Zone2ZoneTravelTimeListener.java">...</a>.
 *
 * Idea of the algorithm:
 * - given n points per zone
 * - find the nearest link and thereof the to-node for each point
 * - this results in n nodes per zone (where some nodes can appear multiple times, this is wanted as it acts as a weight/probability)
 * - for each zone-to-zone combination, calculate the travel times for each node to node combination.
 * - this results in n x n travel times per zone-to-zone combination.
 * - average the n x n travel times and store this value as the zone-to-zone travel time.
 *
 * @author mrieser / SBB
 */
public final class AccessibilityCalculator {

    private AccessibilityCalculator() {
    }

    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public static <T> Map<Node,Double> calculate(Network routingNetwork, Set<Node> origins, Map<T, Double> destinations,
                                                     Map<T, List<Node>> destinationNodes,
                                                     TravelDisutility travelDisutility,
                                                     Vehicle vehicle, Impedance impedance,
                                                     int numberOfThreads) {

        SpeedyGraph routingGraph = new SpeedyGraph(routingNetwork,travelDisutility,PERSON,vehicle);

        // prepare calculation
        ConcurrentHashMap<Node,Double> accessibilityData = new ConcurrentHashMap<>(origins.size());

        // do calculation
        ConcurrentLinkedQueue<Node> originNodes = new ConcurrentLinkedQueue<>(origins);

        Counter counter = new Counter("Calculating accessibility zone ", " / " + origins.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RowWorker<T> worker = new RowWorker<>(originNodes, destinations, routingGraph,
                    destinationNodes, accessibilityData, impedance, counter);
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

        return accessibilityData;
    }

    private static class RowWorker<T> implements Runnable {
        private final ConcurrentLinkedQueue<Node> originNodes;
        private final Map<T, Double> destinations;
        private final SpeedyGraph graph;
        private final Map<T, List<Node>> destinationNodes;
        private final ConcurrentHashMap<Node,Double> accessibilityData;
        private final Impedance impedance;
        private final Counter counter;

        RowWorker(ConcurrentLinkedQueue<Node> originNodes, Map<T, Double> destinations, SpeedyGraph graph,
                  Map<T, List<Node>> destinationNodes, ConcurrentHashMap<Node,Double> accessibilityData,
                  Impedance impedance, Counter counter) {
            this.originNodes = originNodes;
            this.destinations = destinations;
            this.graph = graph;
            this.destinationNodes = destinationNodes;
            this.accessibilityData = accessibilityData;
            this.impedance = impedance;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTreeLite lcpTree = new LeastCostPathTreeLite(this.graph);
            while (true) {
                Node fromNode = this.originNodes.poll();
                if (fromNode == null) {
                    return;
                }

                this.counter.incCounter();
                lcpTree.calculate(fromNode.getId().index());

                double accessibility = 0.;

                for (Map.Entry<T, Double> destination : this.destinations.entrySet()) {

                    double wt = destination.getValue();
                    double cost = Double.MAX_VALUE;

                    for (Node toNode : this.destinationNodes.get(destination.getKey())) {
                        int toNodeIndex = toNode.getId().index();
                        double nodeCost = lcpTree.getCost(toNodeIndex);
                        if (nodeCost < cost) {
                            cost = nodeCost;
                        }
                    }
                    accessibility += impedance.getImpedance(cost) * wt;
                }
                this.accessibilityData.put(fromNode,accessibility);
            }
        }
    }
}