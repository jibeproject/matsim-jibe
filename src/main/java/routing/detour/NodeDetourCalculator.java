/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package routing.detour;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.vehicles.Vehicle;
import resources.Properties;
import resources.Resources;
import routing.graph.LeastCostPathTree3;
import routing.graph.SpeedyGraph;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// Based on the skim matrix calculations from the MATSim SBB Extensions
public final class NodeDetourCalculator {

    NodeDetourCalculator() {
    }

    public static final Logger log = Logger.getLogger(NodeDetourCalculator.class);
    private static final Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));
    private final ConcurrentHashMap<String,double[]> largeDetourData = new ConcurrentHashMap<>();

    private double maxDetour;

    public long[] calculate(Network routingNetwork, Set<Id<Node>> nodes,
                            TravelTime travelTime, TravelDisutility travelDisutility, Vehicle vehicle) {

        int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);

        SpeedyGraph graphFast = new SpeedyGraph(routingNetwork,travelTime,new OnlyTimeDependentTravelDisutility(travelTime),PERSON,vehicle);
        SpeedyGraph graphJibe = new SpeedyGraph(routingNetwork,travelTime,travelDisutility,PERSON,vehicle);

        // do calculation
        ConcurrentLinkedQueue<Id<Node>> originNodes = new ConcurrentLinkedQueue<>(nodes);

        Counter counter = new Counter("Calculating accessibility node ", " / " + nodes.size());
        NodeWorker[] workers = new NodeWorker[numberOfThreads];
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            workers[i] = new NodeWorker(originNodes,nodes,graphFast, graphJibe, counter,largeDetourData);
            threads[i] = new Thread(workers[i], "MaxDetourCalculator-" + i);
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

        // Return densities
        long[] densities = new long[500];
        for (int i = 0; i < numberOfThreads; i++) {
            long[] threadDensities = workers[i].getThreadDensities();
            for(int j = 0 ; j < 500 ; j++) {
                densities[j] += threadDensities[j];
            }
            double threadMaxDetour = workers[i].getMaxDetour();
            log.info("Maximum detour for thread " + i + ": " + threadMaxDetour);
            if(threadMaxDetour > maxDetour) {
                maxDetour = threadMaxDetour;
            }
        }

        log.info("Overall maximum detour: " + maxDetour);
        return densities;


    }

    public ConcurrentHashMap<String,double[]> getLargeDetourData() {
        return largeDetourData;
    }

    private static class NodeWorker implements Runnable {
        private final ConcurrentLinkedQueue<Id<Node>> originNodes;
        private final Set<Id<Node>> destinationNodes;
        private final SpeedyGraph graphFast;
        private final SpeedyGraph graphJibe;
        private final Counter counter;
        private final ConcurrentHashMap<String,double[]> largeDetourData;
        private final long[] threadDensities = new long[500];

        private double maxDetour = 1.;

        NodeWorker(ConcurrentLinkedQueue<Id<Node>> originNodes, Set<Id<Node>> destinationNodes,
                   SpeedyGraph graphFast, SpeedyGraph graphJibe, Counter counter, ConcurrentHashMap<String,double[]> largeDetourData) {
            this.originNodes = originNodes;
            this.destinationNodes = destinationNodes;
            this.graphFast = graphFast;
            this.graphJibe = graphJibe;
            this.counter = counter;
            this.largeDetourData = largeDetourData;
        }

        public void run() {
            LeastCostPathTree3 lcpTreeFast;
            LeastCostPathTree3 lcpTreeJibe;

            while (true) {
                Id<Node> fromNodeId = this.originNodes.poll();
                if (fromNodeId == null) {
                    return;
                }

                this.counter.incCounter();

                lcpTreeFast = new LeastCostPathTree3(this.graphFast);
                lcpTreeJibe = new LeastCostPathTree3(this.graphJibe);

                lcpTreeFast.calculate(fromNodeId.index(),0.,true);
                lcpTreeJibe.calculate(fromNodeId.index(),0.,true);

                for (Id<Node> toNodeId : destinationNodes) {

                    // Check if node is in JIBE tree
                    OptionalTime timeJibe = lcpTreeJibe.getTime(toNodeId.index());
                    if(timeJibe.isUndefined()) {
                        continue;
                    }

                    // Check if node is included in fastest tree
                    OptionalTime timeFast = lcpTreeFast.getTime(toNodeId.index());
                    if(timeFast.isUndefined()) {
                        throw new RuntimeException("Node included in JIBE tree but not fastest tree");
                    }

                    // Set maximum detour
                    double detour = timeJibe.seconds() / timeFast.seconds();
                    if(detour > maxDetour) {
                        maxDetour = detour;
                    }

                    int index = (int) ((detour - 1)*100);
                    if(index > 499) {
                        index = 499;
                    }
                    threadDensities[index]++;

                    // Capture really large detours and figure out what's going on
                    if(detour > 6.) {
                        largeDetourData.put(fromNodeId + "," + toNodeId,new double[] {timeFast.seconds(),timeJibe.seconds()});
                    }

                }
            }
        }

        public double getMaxDetour() {return maxDetour; }

        public long[] getThreadDensities() {
            return threadDensities;
        }

    }
}