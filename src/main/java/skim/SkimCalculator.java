/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package skim;

import gis.GisUtils;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import resources.Properties;
import resources.Resources;
import routing.graph.LcpTree1Way;
import routing.graph.SpeedyGraph;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class SkimCalculator {

    private final static Logger logger = Logger.getLogger(SkimCalculator.class);

    private final Map<String, double[][]> results = new LinkedHashMap<>();
    private final int dimSize;

    private final Geometry regionBoundary;

    private final Set<Integer> zoneIds;
    private final Map<Integer, Integer> id2index;
    private final Map<Integer, SimpleFeature> zones;

    private final int numberOfThreads;

    public SkimCalculator(Map<Integer, SimpleFeature> zones) throws IOException {
        this.numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);
        this.regionBoundary = GpkgReader.readRegionBoundary();
        this.dimSize = zones.size();
        this.zoneIds = zones.keySet();
        this.zones = zones;
        this.id2index = new HashMap<>(this.dimSize);
        int i = 0;
        for(int zoneId : this.zoneIds) {
            this.id2index.put(zoneId,i);
            i++;
        }
    }

    public void calculate(String name, Network routingNetwork, Network xy2lNetwork,
                                                TravelTime travelTime,
                                                TravelDisutility travelDisutility,
                                                Vehicle vehicle) {

        SpeedyGraph routingGraph = new SpeedyGraph(routingNetwork, travelTime,travelDisutility, null, vehicle);

        double[][] costs = new double[dimSize][dimSize];
        int[] nodeCountPerZoneIdx = new int[dimSize];

        // Compute network nodes
        Set<Id<Node>> gmNodes = NetworkUtils2.getNodesInBoundary(xy2lNetwork,regionBoundary);
        Map<SimpleFeature, IdSet<Node>> zoneNodesMap = GisUtils.assignNodesToZones(zones.values(),gmNodes,xy2lNetwork);
        Map<SimpleFeature, IdSet<Link>> zoneLinksMap = GisUtils.calculateLinksIntersectingZones(zones.values(),xy2lNetwork);

        Map<Integer, IdSet<Node>> zoneIdNodesMap = new HashMap<>(zones.size());
        for(Map.Entry<Integer, SimpleFeature> e : zones.entrySet()) {
            int zoneId = e.getKey();
            SimpleFeature zone = e.getValue();
            IdSet<Node> nodeIDs = zoneNodesMap.get(zone);
            if(nodeIDs == null) {
                logger.error("No nodes inside zone " + zoneId + ". Using to-nodes of intersecting links instead." );
                nodeIDs = new IdSet<>(Node.class);
                IdSet<Link> intersectingLinks = zoneLinksMap.get(zone);
                if(intersectingLinks != null) {
                    for(Id<Link> linkId : intersectingLinks) {
                        nodeIDs.add(xy2lNetwork.getLinks().get(linkId).getToNode().getId());
                    }
                } else {
                    Point p = ((Geometry) zone.getDefaultGeometry()).getCentroid();
                    Coord c = new Coord(p.getX(),p.getY());
                    Id<Node> n = NetworkUtils.getNearestLinkExactly(xy2lNetwork,c).getToNode().getId();
                    logger.error("No intersecting links either. Using link closest to centroid. Node = " + n.toString());
                    nodeIDs.add(n);
                }
            }
            zoneIdNodesMap.put(zoneId,nodeIDs);
            nodeCountPerZoneIdx[id2index.get(zoneId)] = nodeIDs.size();
        }

        // do calculation
        ConcurrentLinkedQueue<Integer> originZones = new ConcurrentLinkedQueue<>(zoneIds);

        Counter counter = new Counter("CostCalculator zone ", " / " + zoneIds.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RowWorker worker = new RowWorker(originZones, zoneIds, routingGraph, zoneIdNodesMap, nodeCountPerZoneIdx, costs, this.id2index, counter);
            threads[i] = new Thread(worker, "CostCalculator-" + i);
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

        results.put(name,costs);
    }

    public Map<String,double[][]> getResults() {
        return this.results;
    }

    public void clearResults() {
        this.results.clear();
    }

    public Map<Integer,Integer> getId2index() {
        return this.id2index;
    }

    private static class RowWorker implements Runnable {
        private final ConcurrentLinkedQueue<Integer> originZones;
        private final Set<Integer> destinationZones;
        private final SpeedyGraph graph;
        private final Map<Integer, Integer> id2index;
        private final Map<Integer, IdSet<Node>> zoneNodeMap;
        private final double[][] costs;
        private final int[] nodeCountPerZoneIdx;
        private final Counter counter;


        RowWorker(ConcurrentLinkedQueue<Integer> originZones, Set<Integer> destinationZones, SpeedyGraph graph,
                  Map<Integer, IdSet<Node>> zoneNodeMap, int[] nodeCountPerZoneIdx, double[][] costs,
                  Map<Integer, Integer> id2index, Counter counter) {
            this.originZones = originZones;
            this.destinationZones = destinationZones;
            this.graph = graph;
            this.zoneNodeMap = zoneNodeMap;
            this.nodeCountPerZoneIdx = nodeCountPerZoneIdx;
            this.costs = costs;
            this.id2index = id2index;
            this.counter = counter;
        }

        public void run() {

            LcpTree1Way lcpTree = new LcpTree1Way(this.graph, true);

            while (true) {
                Integer fromZoneId = this.originZones.poll();
                if (fromZoneId == null) {
                    return;
                }

                this.counter.incCounter();
                int fromZoneIdx = id2index.get(fromZoneId);

                double[] results = new double[costs[fromZoneIdx].length];

                for(Id<Node> fromNodeId : this.zoneNodeMap.get(fromZoneId)) {
                    lcpTree.calculate(fromNodeId.index(), 0);

                    for (int toZoneId : this.destinationZones) {
                        int toZoneIdx = id2index.get(toZoneId);
                        for(Id<Node> toNodeId : this.zoneNodeMap.get(toZoneId)) {
                            results[toZoneIdx] += lcpTree.getCost(toNodeId.index());
                        }
                    }
                }

                for(int i = 0 ; i < results.length ; i++) {
                    costs[fromZoneIdx][i] = results[i] / (nodeCountPerZoneIdx[fromZoneIdx] * nodeCountPerZoneIdx[i]);
                }

            }
        }
    }
}
