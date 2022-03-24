/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.skims;

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
public final class GeometryCalculator {

    private GeometryCalculator() {
    }

    public static <T> GeometryData<T> calculate(Network routingNetwork, Set<T> origins, Set<T> destinations, Map<T, Node> zoneNodeMap,
                                                TravelTime travelTime, TravelDisutility travelDisutility, int numberOfThreads) {
        Graph routingGraph = new Graph(routingNetwork);

        // prepare calculation
        GeometryData<T> geometryData = new GeometryData<>(origins, destinations);

        // do calculation
        ConcurrentLinkedQueue<T> originZones = new ConcurrentLinkedQueue<>(origins);

        Counter counter = new Counter("PathGeometries zone ", " / " + origins.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RowWorker<T> worker = new RowWorker<>(originZones, destinations, routingGraph, zoneNodeMap, geometryData, travelTime, travelDisutility, counter);
            threads[i] = new Thread(worker, "PathGeometries-" + i);
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

        return geometryData;
    }

    private static class RowWorker<T> implements Runnable {
        private final ConcurrentLinkedQueue<T> originZones;
        private final Set<T> destinationZones;
        private final Graph graph;
        private final Map<T, Node> zoneNodeMap;
        private final GeometryData<T> geometryData;
        private final TravelTime travelTime;
        private final TravelDisutility travelDisutility;
        private final Counter counter;

        private final static Vehicle VEHICLE = VehicleUtils.getFactory().createVehicle(Id.create("theVehicle", Vehicle.class), VehicleUtils.getDefaultVehicleType());
        private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

        RowWorker(ConcurrentLinkedQueue<T> originZones, Set<T> destinationZones, Graph graph, Map<T, Node> zoneNodeMap, GeometryData<T> geometryData,
                  TravelTime travelTime, TravelDisutility travelDisutility, Counter counter) {
            this.originZones = originZones;
            this.destinationZones = destinationZones;
            this.graph = graph;
            this.zoneNodeMap = zoneNodeMap;
            this.geometryData = geometryData;
            this.travelTime = travelTime;
            this.travelDisutility = travelDisutility;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree lcpTree = new LeastCostPathTree(this.graph, this.travelTime, this.travelDisutility, null);

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
                            int[] nodesPassed = lcpTree.getNodeArray(toNode.getId().index());
                            int[] linksTravelled = lcpTree.getLinkArray(toNode.getId().index());
                            this.geometryData.nodeGeometries.set(fromZoneId,toZoneId,nodesPassed);
                            this.geometryData.linksTravelled.set(fromZoneId,toZoneId,linksTravelled);
                        }
                    }
                }
            }
        }
    }
}
