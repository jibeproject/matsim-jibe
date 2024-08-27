package estimation.dynamic;

import estimation.utilities.AbstractUtilityFunction;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import resources.Properties;
import resources.Resources;
import routing.Gradient;
import routing.disutility.JibeDisutility2;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import trip.Place;
import trip.Trip;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class DynamicRouter implements DynamicUtilityComponent {

    private final static Logger logger = Logger.getLogger(DynamicRouter.class);
    private final int numberOfThreads;
    final Trip[] trips;
    final Node[] originNodes;
    final Node[] destinationNodes;
    final String mode;
    final Set<SimpleFeature> zones;
    final Network network;
    final TravelTime tt;
    final AbstractUtilityFunction u;
    final Vehicle vehicle;
    final int betaCostIdx;
    final int gammaGradIdx;
    final int gammaVgviInx;
    final int gammaStressLinkInx;
    final int gammaStressJctIdx;
    final IntrazonalCostCalculator intrazonalCostCalculator;
    double betaCost;
    double mGrad;
    double mVgvi;
    double mStressLink;
    double mStressJct;
    final double[] cost;
    final double[] time;
    final double[] grad;
    final double[] vgvi;
    final double[] stressLink;
    final double[] stressJct;


    public DynamicRouter(Trip[] trips, AbstractUtilityFunction u, String mode, Set<SimpleFeature> zones,
                         Network network, Vehicle vehicle, TravelTime tt, String betaCost,
                         String gammaGrad, String gammaVgvi, String gammaStressLink, String gammaStressJct) {
        this.trips = trips;
        this.u = u;
        this.mode = mode;
        this.zones = zones;
        this.network = network;
        this.vehicle = vehicle;
        this.tt = tt;
        this.betaCostIdx = u.getCoeffIdx(betaCost);
        this.gammaGradIdx = u.getCoeffIdx(gammaGrad);
        this.gammaVgviInx = u.getCoeffIdx(gammaVgvi);
        this.gammaStressLinkInx = u.getCoeffIdx(gammaStressLink);
        this.gammaStressJctIdx = u.getCoeffIdx(gammaStressJct);
        this.numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);

        // Initialise result values
        int tripCount = trips.length;
        cost = new double[tripCount];
        time = new double[tripCount];
        grad = new double[tripCount];
        vgvi = new double[tripCount];
        stressLink = new double[tripCount];
        stressJct = new double[tripCount];

        // Initialise origin/destination nodes
        originNodes = new Node[tripCount];
        destinationNodes = new Node[tripCount];

        // Sort inter/intra-zonal trips, and compute fixed results for intrazonal
        intrazonalCostCalculator = new IntrazonalCostCalculator(trips,u,zones, network, mode);

        for(int i = 0 ; i < tripCount ; i++) {
            Trip trip = trips[i];
            if (trip.getZone(Place.ORIGIN).equals(trip.getZone(Place.DESTINATION))) {
                time[i] = intrazonalCostCalculator.getTime(i);
                grad[i] = intrazonalCostCalculator.getGradient(i);
                vgvi[i] = intrazonalCostCalculator.getVgvi(i);
                stressLink[i] = intrazonalCostCalculator.getStressLink(i);
                stressJct[i] = intrazonalCostCalculator.getStressJct(i);
            } else if (trip.routable(Place.ORIGIN,Place.DESTINATION)) {
                originNodes[i] = network.getNodes().get(NetworkUtils.getNearestLinkExactly(network, trip.getCoord(ORIGIN)).getToNode().getId());
                destinationNodes[i] = network.getNodes().get(NetworkUtils.getNearestLinkExactly(network, trip.getCoord(DESTINATION)).getToNode().getId());
            } else {
                throw new RuntimeException("Household " + trip.getHouseholdId() + " Person " + trip.getPersonId() +
                        " Trip " + trip.getTripId() + " outside boundary! It shouldn't be here!");
            }
        }
    }

    public double getCost(int i) {
        return cost[i];
    }

    public double getDerivGammaGrad(int i) {
        return betaCost * mGrad * grad[i];
    }

    public double getDerivGammaVgvi(int i) {
        return betaCost * mVgvi * vgvi[i];
    }

    public double getDerivGammaStressLink(int i) {
        return betaCost * mStressLink * stressLink[i];
    }

    public double getDerivGammaStressJct(int i) {
        return betaCost * mStressJct * stressJct[i];
    }

    public void update(double[] x) {
        ConcurrentLinkedQueue<Integer> tripsQueue = IntStream.range(0, trips.length).boxed().collect(Collectors.toCollection(ConcurrentLinkedQueue::new));

        // Update cost coefficient
        betaCost = x[betaCostIdx];
        mGrad = Math.exp(x[gammaGradIdx]);
        mVgvi = Math.exp(x[gammaVgviInx]);
        mStressLink = Math.exp(x[gammaStressLinkInx]);
        mStressJct = Math.exp(x[gammaStressJctIdx]);

        logger.info(mode.toUpperCase() + " NEW COEFFS: Cost = " + x[betaCostIdx] + " Gradient = " + x[gammaGradIdx] + ". VGVI = " + x[gammaVgviInx] +
                ". LinkStress = " + x[gammaStressLinkInx] + ". JunctionStress = " + x[gammaStressJctIdx] +
                "\n NEW MARGINAL COSTS: Grad = " + mGrad + " VGVI = " + mVgvi + " LinkStress = " + mStressLink + " JctStress = " + mStressJct);

        // Update costs for intrazonal trips
        intrazonalCostCalculator.updateCosts(mGrad,mVgvi,mStressLink,mStressJct);

        // New disutility function for interzonal trips
        JibeDisutility2 disutility = new JibeDisutility2(network,vehicle,mode,tt,mGrad,mVgvi,mStressLink,mStressJct);

        // Empty costs
        Arrays.fill(cost,0.);

        // Update all
        Thread[] threads = new Thread[numberOfThreads];
        Counter counter = new Counter("Routed ", " / " + trips.length + " trips.");
        for(int i = 0 ; i < numberOfThreads ; i++) {
            LeastCostPathCalculator dijkstra = new FastDijkstraFactory(false).createPathCalculator(network,disutility, tt);

            TripWorker worker = new TripWorker(tripsQueue,originNodes,destinationNodes,counter,mode,tt,vehicle,dijkstra,intrazonalCostCalculator,
                    cost,time,grad,vgvi,stressLink,stressJct);

            threads[i] = new Thread(worker,"DynamicRouteUpdate-" + i);
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

        logger.info("Completed dynamic update for mode " + mode);
    }

    static class TripWorker implements Runnable {

        private final ConcurrentLinkedQueue<Integer> tripIndices;
        private final Node[] originNodes;
        private final Node[] destinationNodes;
        private final Counter counter;
        private final Vehicle vehicle;
        private final LeastCostPathCalculator pathCalculator;
        private final TravelTime tt;
        private final String mode;
        private final IntrazonalCostCalculator intrazonalCostCalculator;
        private final double[] cost;
        private final double[] time;
        private final double[] gradient;
        private final double[] vgvi;
        private final double[] linkStress;
        private final double[] jctStress;


        public TripWorker(ConcurrentLinkedQueue<Integer> tripIndices, Node[] originNodes, Node[] destinationNodes,
                          Counter counter, String mode, TravelTime travelTime, Vehicle vehicle,
                          LeastCostPathCalculator pathCalculator, IntrazonalCostCalculator intrazonalCostCalculator,
                          double[] cost, double[] time, double[] gradient, double[] vgvi, double[] linkStress, double[] jctStress) {
            this.tripIndices = tripIndices;
            this.originNodes = originNodes;
            this.destinationNodes = destinationNodes;
            this.counter = counter;
            this.mode = mode;
            this.tt = travelTime;
            this.vehicle = vehicle;
            this.pathCalculator = pathCalculator;
            this.intrazonalCostCalculator = intrazonalCostCalculator;
            this.cost = cost;
            this.time = time;
            this.gradient = gradient;
            this.vgvi = vgvi;
            this.linkStress = linkStress;
            this.jctStress = jctStress;
        }

        public void run() {

            while(true) {
                Integer tripIdx = this.tripIndices.poll();
                if(tripIdx == null) {
                    return;
                }
                this.counter.incCounter();
                if(!intrazonalCostCalculator.isIntrazonal(tripIdx)) {
                    LeastCostPathCalculator.Path path = pathCalculator.calcLeastCostPath(originNodes[tripIdx], destinationNodes[tripIdx], 0., null, vehicle);
                    double tripGradient = 0.;
                    double tripVgvi = 0.;
                    double tripStressLink = 0.;
                    double tripStressJct = 0.;
                    for(Link link : path.links) {
                        double linkTime = tt.getLinkTravelTime(link,0.,null,vehicle);
                        tripGradient += linkTime * Math.max(Math.min(Gradient.getGradient(link),0.5),0.);
                        tripVgvi += linkTime * Math.max(0.,0.81 - LinkAmbience.getVgviFactor(link));
                        tripStressLink += linkTime * LinkStress.getStress(link,mode);
                        if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                            double linkLength = link.getLength();
                            double junctionWidth = Math.min(linkLength,(double) link.getAttributes().getAttribute("crossWidth"));
                            tripStressJct += linkTime * (junctionWidth / linkLength) * JctStress.getStress(link,mode);
                        }
                    }
                    cost[tripIdx] = path.travelCost;
                    time[tripIdx] = path.travelTime;
                    gradient[tripIdx] = tripGradient;
                    vgvi[tripIdx] = tripVgvi;
                    linkStress[tripIdx] = tripStressLink;
                    jctStress[tripIdx] = tripStressJct;
                } else {
                    // For intrazonal, only need update cost since everything else stays the same
                    cost[tripIdx] = intrazonalCostCalculator.getCost(tripIdx);
                }
            }
        }
    }
}
