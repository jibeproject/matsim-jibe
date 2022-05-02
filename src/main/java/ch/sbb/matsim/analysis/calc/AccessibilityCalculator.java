/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.calc;

import ch.sbb.matsim.analysis.Impedance;
import ch.sbb.matsim.analysis.TravelAttribute;
import ch.sbb.matsim.analysis.data.AccessibilityData;
import ch.sbb.matsim.routing.graph.Graph;
import ch.sbb.matsim.routing.graph.LeastCostPathTree;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Calculates zone-to-zone matrices containing a number of performance indicators related to modes routed on a network.
 *
 * Inspired by https://github.com/moeckel/silo/blob/siloMatsim/silo/src/main/java/edu/umd/ncsg/transportModel/Zone2ZoneTravelTimeListener.java.
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

    public static <T> AccessibilityData<T> calculate(Network routingNetwork, Set<T> origins, Set<T> destinations,
                                                     Map<T, Node> zoneNodeMap, Map<T,Double> zoneWeightMap,
                                                     TravelTime travelTime, TravelDisutility travelDisutility, TravelAttribute[] travelAttributes,
                                                     Impedance impedance,
                                                     int numberOfThreads) {
        Graph routingGraph = new Graph(routingNetwork);

        // prepare calculation
        int attributeCount = travelAttributes != null ? travelAttributes.length : 0;
        AccessibilityData<T> accessibilityData = new AccessibilityData<>(origins, attributeCount);

        // do calculation
        ConcurrentLinkedQueue<T> originZones = new ConcurrentLinkedQueue<>(origins);

        Counter counter = new Counter("NetworkRouting zone ", " / " + origins.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RowWorker<T> worker = new RowWorker<>(originZones, destinations, routingGraph, zoneNodeMap, zoneWeightMap, accessibilityData,
                    travelTime, travelDisutility, travelAttributes, impedance, counter);
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
        private final Set<T> destinationZones;
        private final Graph graph;
        private final Map<T, Node> zoneNodeMap;
        private final Map<T, Double> zoneWeightMap;
        private final AccessibilityData<T> accessibilityData;
        private final TravelTime travelTime;
        private final TravelDisutility travelDisutility;
        private final TravelAttribute[] travelAttributes;
        private final Impedance impedance;
        private final int attributeCount;
        private final Counter counter;

        private final static Vehicle VEHICLE = VehicleUtils.getFactory().createVehicle(Id.create("theVehicle", Vehicle.class), VehicleUtils.getDefaultVehicleType());
        private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

        RowWorker(ConcurrentLinkedQueue<T> originZones, Set<T> destinationZones, Graph graph, Map<T, Node> zoneNodeMap,
                  Map<T, Double> zoneWeightMap, AccessibilityData<T> accessibilityData,
                  TravelTime travelTime, TravelDisutility travelDisutility, TravelAttribute[] travelAttributes,
                  Impedance impedance, Counter counter) {
            this.originZones = originZones;
            this.destinationZones = destinationZones;
            this.graph = graph;
            this.zoneNodeMap = zoneNodeMap;
            this.zoneWeightMap = zoneWeightMap;
            this.accessibilityData = accessibilityData;
            this.travelTime = travelTime;
            this.travelDisutility = travelDisutility;
            this.travelAttributes = travelAttributes;
            this.impedance = impedance;
            this.attributeCount = travelAttributes != null ? travelAttributes.length : 0;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree lcpTree = new LeastCostPathTree(this.graph, this.travelTime, this.travelDisutility, this.travelAttributes);
            while (true) {
                T fromZoneId = this.originZones.poll();
                if (fromZoneId == null) {
                    return;
                }

                this.counter.incCounter();
                Node fromNode = this.zoneNodeMap.get(fromZoneId);
                if (fromNode != null) {
                    lcpTree.calculate(fromNode.getId().index(), 0, PERSON, VEHICLE);

                    for (T toZoneId : this.destinationZones) {
                        Node toNode = this.zoneNodeMap.get(toZoneId);
                        if (toNode != null) {

                            int nodeIndex = toNode.getId().index();
                            double dist = lcpTree.getDistance(nodeIndex);
                            double distImpedance = impedance.getImpedance(dist);
                            double weight = distImpedance * zoneWeightMap.get(toZoneId);

                            this.accessibilityData.addDist(fromZoneId, weight);

                            for(int i = 0 ; i < attributeCount ; i++) {
                                double attr = lcpTree.getAttribute(nodeIndex, i);
                                this.accessibilityData.addAttr(fromZoneId, i, attr * weight);
                            }

                        } else {
                            // this might happen if a zone has no geometry, for whatever reason...
                            System.out.println("Entry " + fromZoneId + "-" + toZoneId + " has no geometry.");
                            //this.accessibilityData.addTime(fromZoneId, Float.POSITIVE_INFINITY);
                            this.accessibilityData.addDist(fromZoneId, Float.POSITIVE_INFINITY);
                        }
                    }
                } else {
                    // this might happen if a zone has no geometry, for whatever reason...
                    for (T toZoneId : this.destinationZones) {
                        System.out.println("Entry " + fromZoneId + "-" + toZoneId + " has no geometry.");
                        //this.accessibilityData.addTime(fromZoneId, Float.POSITIVE_INFINITY);
                        this.accessibilityData.addDist(fromZoneId, Float.POSITIVE_INFINITY);
                    }
                }
            }
        }
    }
}
