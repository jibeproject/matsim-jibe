/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility.old;

import accessibility.impedance.DecayFunction;
import ch.sbb.matsim.analysis.TravelAttribute;
import ch.sbb.matsim.analysis.data.AccessibilityData;
import routing.graph.Graph;
import routing.graph.LeastCostPathTree2;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
public final class AccessibilityCalculatorOld {

    private AccessibilityCalculatorOld() {
    }

    public static <T> AccessibilityData<T> calculate(Network routingNetwork, Set<T> origins, Map<T, Double> destinations,
                                                     Map<T, List<Node>> originNodes, Map<T, List<Node>> destinationNodes,
                                                     TravelTime travelTime, TravelDisutility travelDisutility,
                                                     TravelAttribute[] travelAttributes, Vehicle vehicle,
                                                     DecayFunction decayFunction,
                                                     int numberOfThreads) {
        Graph routingGraph = new Graph(routingNetwork);

        // prepare calculation
        int attributeCount = travelAttributes != null ? travelAttributes.length : 0;
        AccessibilityData<T> accessibilityData = new AccessibilityData<>(origins, attributeCount);

        // do calculation
        ConcurrentLinkedQueue<T> originZones = new ConcurrentLinkedQueue<>(origins);

        Counter counter = new Counter("Calculating accessibility zone ", " / " + origins.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RowWorker<T> worker = new RowWorker<>(originZones, destinations, routingGraph, originNodes, destinationNodes, accessibilityData,
                    travelTime, travelDisutility, travelAttributes, vehicle, decayFunction, counter);
            threads[i] = new Thread(worker, "NetworkRouting-" + i);
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
        private final ConcurrentLinkedQueue<T> originZones;
        private final Map<T, Double> destinations;
        private final Graph graph;
        private final Map<T, List<Node>> originNodes;
        private final Map<T, List<Node>> destinationNodes;
        private final AccessibilityData<T> accessibilityData;
        private final TravelTime travelTime;
        private final TravelDisutility travelDisutility;
        private final TravelAttribute[] travelAttributes;
        private final Vehicle vehicle;
        private final DecayFunction decayFunction;
        private final int attributeCount;
        private final Counter counter;

        private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

        RowWorker(ConcurrentLinkedQueue<T> originZones, Map<T, Double> destinations, Graph graph,
                  Map<T, List<Node>> originNodes, Map<T, List<Node>> destinationNodes, AccessibilityData<T> accessibilityData,
                  TravelTime travelTime, TravelDisutility travelDisutility, TravelAttribute[] travelAttributes,
                  Vehicle vehicle, DecayFunction decayFunction, Counter counter) {
            this.originZones = originZones;
            this.destinations = destinations;
            this.graph = graph;
            this.originNodes = originNodes;
            this.destinationNodes = destinationNodes;
            this.accessibilityData = accessibilityData;
            this.travelTime = travelTime;
            this.travelDisutility = travelDisutility;
            this.travelAttributes = travelAttributes;
            this.vehicle = vehicle;
            this.decayFunction = decayFunction;
            this.attributeCount = travelAttributes != null ? travelAttributes.length : 0;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree2 lcpTree = new LeastCostPathTree2(this.graph, this.travelTime, this.travelDisutility, this.travelAttributes);
            while (true) {
                T fromZoneId = this.originZones.poll();
                if (fromZoneId == null) {
                    return;
                }

                this.counter.incCounter();
                List<Node> fromNodes = this.originNodes.get(fromZoneId);
                int samplingPoints = fromNodes.size();
                for(Node fromNode : fromNodes) {
                    if (fromNode != null) {
                        lcpTree.calculate(fromNode.getId().index(), 0, PERSON, vehicle);

                        double accessibility = 0.;
                        double[] attributes = new double[attributeCount];

                        for (Map.Entry<T,Double> destination : this.destinations.entrySet()) {

                            double wt = destination.getValue();
                            double cost = Double.MAX_VALUE;
                            int index = -1;

                            for(Node toNode : this.destinationNodes.get(destination.getKey())) {
                                int toNodeIndex = toNode.getId().index();
                                double nodeCost = lcpTree.getCost(toNodeIndex);
                                if(nodeCost < cost) {
                                    cost = nodeCost;
                                    index = toNodeIndex;
                                }
                            }

                            double value = decayFunction.getDecay(cost) * wt;
                            accessibility += value;

                            for(int i = 0 ; i < attributeCount ; i++) {
                                double attr = lcpTree.getAttribute(index, i);
                                attributes[i] += attr * value;
                            }
                        }

                        this.accessibilityData.setAccessibility(fromZoneId, accessibility / samplingPoints);
                        if(attributeCount > 0) {
                            this.accessibilityData.setAttributes(fromZoneId, attributes, samplingPoints);
                        }

                    } else {
                        // this might happen if a zone has no geometry, for whatever reason...
                        throw new RuntimeException("Entry " + fromZoneId + " has no geometry.");
                    }
                }
            }
        }
    }
}
