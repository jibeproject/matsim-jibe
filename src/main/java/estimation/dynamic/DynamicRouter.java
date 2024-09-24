package estimation.dynamic;

import estimation.utilities.AbstractUtilitySpecification;
import gis.GisUtils;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class DynamicRouter implements DynamicUtilityComponent {

    private final static boolean ENABLE_DYNAMIC_ROUTING = false;
    private final static Logger logger = Logger.getLogger(DynamicRouter.class);
    private final int numberOfThreads;
    final Trip[] trips;
    final int[] tripDisutilityType;
    final String mode;
    final Network network;
    final TravelTime tt;
    final AbstractUtilitySpecification u;
    final Vehicle vehicle;
    final int gammaGradPos;
    final int gammaVgviPos;
    final int gammaStressLinkPos;
    final int gammaStressLinkFemalePos;
    final int gammaStressLinkUnder15Pos;
    final int gammaStressJctPos;
    final int gammaStressJctFemalePos;
    final int gammaStressJctUnder15Pos;
    final PathData pathData;


    public DynamicRouter(Trip[] trips, AbstractUtilitySpecification u, String mode, Set<SimpleFeature> zones,
                         Network network, Vehicle vehicle, TravelTime tt,
                         String gammaGrad, String gammaVgvi,
                         String gammaStressLink, String gammaStressLinkFemale, String gammaStressLinkUnder15,
                         String gammaStressJct, String gammaStressJctFemale, String gammaStressJctUnder15) {
        this.trips = trips;
        this.u = u;
        this.mode = mode;
        this.network = network;
        this.vehicle = vehicle;
        this.tt = tt;
        this.gammaGradPos = u.getCoeffPos(gammaGrad);
        this.gammaVgviPos = u.getCoeffPos(gammaVgvi);
        this.gammaStressLinkPos = u.getCoeffPos(gammaStressLink);
        this.gammaStressLinkFemalePos = u.getCoeffPos(gammaStressLinkFemale);
        this.gammaStressLinkUnder15Pos = u.getCoeffPos(gammaStressLinkUnder15);
        this.gammaStressJctPos = u.getCoeffPos(gammaStressJct);
        this.gammaStressJctFemalePos = u.getCoeffPos(gammaStressJctFemale);
        this.gammaStressJctUnder15Pos = u.getCoeffPos(gammaStressJctUnder15);
        this.numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);

        // Set disutility types
        tripDisutilityType = new int[trips.length];
        for(int i = 0 ; i < trips.length ; i++) {
            if(u.value(i,"p.age_group_agg_5_14") == 1) {
                tripDisutilityType[i] = 2;  // CHILD UNDER 15
            } else if(u.value(i,"p.female") == 1) {
                tripDisutilityType[i] = 1;  // FEMALE
            } else {
                tripDisutilityType[i] = 0;  // MALE
            }
        }

        // Sort inter/intra-zonal trips, and compute fixed results for intrazonal
        pathData = new PathData(trips.length);
        computeIntrazonalTripData(pathData,trips,u,zones,network,mode);

        // Origin and destination nodes
        computeOriginAndDestinationNodes(pathData,trips,network);

        // use in debugger to check zero costs: IntStream.range(0,cost.length).filter(i->cost[i] == 0).toArray();
        // Compute costs for starting values
        logger.info("Running initial routing estimate...");
        updatePathData(u.getStarting());
    }

    private static void computeOriginAndDestinationNodes(PathData pathData,Trip[] trips,Network network) {
        for (int i = 0; i < trips.length; i++) {
            Trip trip = trips[i];
            if (!pathData.intrazonal[i]) {
                if (trip.routable(ORIGIN, DESTINATION)) {
                    pathData.originNodes[i] = getNode(trip, ORIGIN, network);
                    pathData.destinationNodes[i] = getNode(trip, DESTINATION, network);
                } else {
                    throw new RuntimeException("Household " + trip.getHouseholdId() + " Person " + trip.getPersonId() +
                            " Trip " + trip.getTripId() + " outside boundary! It shouldn't be here!");
                }
            }
        }
    }
    private static Node getNode(Trip trip, Place place, Network net) {
        if(trip.getZone(place).equals("E00030420")) {
            return net.getNodes().get(Id.createNodeId(173563));
        } else {
            return net.getNodes().get(NetworkUtils.getNearestLinkExactly(net, trip.getCoord(place)).getToNode().getId());
        }
    }

    public double getTime(int i) {
        return pathData.time[i];
    }

    public double getGrad(int i) {
        return pathData.grad[i];
    }

    public double getVgvi(int i) {
        return pathData.vgvi[i];
    }

    public double getStressLink(int i) {
        return pathData.stressLink[i];
    }

    public double getStressJct(int i) {
        return pathData.stressJct[i];
    }

    public void update(double[] xVarOnly) {
        if(ENABLE_DYNAMIC_ROUTING) {
            updatePathData(xVarOnly);
        }
    }

    private void updatePathData(double[] xVarOnly) {

        // GET LATEST COEFFICIENTS
        double[] x = u.expandCoeffs(xVarOnly);

        // Gradient & VGVI
        double mGrad = zeroIfNegative(x[gammaGradPos],"gradient");
        double mVgvi = zeroIfNegative(x[gammaVgviPos],"VGVI");

        // Link stress + adjustments
        double mStressLinkMale = zeroIfNegative(x[gammaStressLinkPos],"link stress (baseline)");
        double mStressLinkFemale = zeroIfNegative(x[gammaStressLinkPos] + x[gammaStressLinkFemalePos],"link stress (female)");
        double mStressLinkUnder15 = zeroIfNegative(x[gammaStressLinkPos] + x[gammaStressLinkUnder15Pos],"link stress (under 15)");

        // Junction stress + adjustments
        double mStressJctMale = zeroIfNegative(x[gammaStressJctPos],"junction stress (baseline)");
        double mStressJctFemale = zeroIfNegative(x[gammaStressJctPos] + x[gammaStressJctFemalePos],"junction stress (female)");
        double mStressJctUnder15 = zeroIfNegative(x[gammaStressJctPos] + x[gammaStressJctUnder15Pos],"junction stress (under 15)");

        // Update all routable trips (multithreaded)
        TravelDisutility disutilityMale = new JibeDisutility2(network, vehicle, mode, tt, mGrad, mVgvi, mStressLinkMale, mStressJctMale);
        TravelDisutility disutilityFemale = new JibeDisutility2(network, vehicle, mode, tt, mGrad, mVgvi, mStressLinkFemale, mStressJctFemale);
        TravelDisutility disutilityUnder15 = new JibeDisutility2(network, vehicle, mode, tt, mGrad, mVgvi, mStressLinkUnder15, mStressJctUnder15);

        Thread[] threads = new Thread[numberOfThreads];
        Counter counter = new Counter("Routed ", " / " + trips.length + " trips.");
        ConcurrentLinkedQueue<Integer> tripsQueue = IntStream.range(0, trips.length).boxed().collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
        for(int i = 0 ; i < numberOfThreads ; i++) {
            LeastCostPathCalculator[] lcpCalculators = new LeastCostPathCalculator[3];
            lcpCalculators[0] = new FastDijkstraFactory(false).createPathCalculator(network, disutilityMale, tt);
            lcpCalculators[1] = new FastDijkstraFactory(false).createPathCalculator(network, disutilityFemale, tt);
            lcpCalculators[2] = new FastDijkstraFactory(false).createPathCalculator(network, disutilityUnder15, tt);
            TripWorker worker = new TripWorker(tripsQueue,counter,mode,tt,vehicle,lcpCalculators,tripDisutilityType,pathData);
            threads[i] = new Thread(worker,"DynamicRouteUpdate-" + i);
            threads[i].start();
        }

        // wait until all threads have finished...
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static double zeroIfNegative(double n, String name) {
        if(n < 0) {
            logger.warn("Negative " + name + " coefficient:" + n + ". Set to 0 for routing...");
            return 0.;
        } else {
            return n;
        }
    }

    public static class PathData {
        final int length;
        final boolean[] intrazonal;
        final Node[] originNodes;
        final Node[] destinationNodes;
        final double[] time;
        final double[] grad;
        final double[] vgvi;
        final double[] stressLink;
        final double[] stressJct;

        public PathData(int length) {
            this.length = length;
            this.intrazonal = new boolean[length];
            this.originNodes = new Node[length];
            this.destinationNodes = new Node[length];
            this.time = new double[length];
            this.grad = new double[length];
            this.vgvi = new double[length];
            this.stressLink = new double[length];
            this.stressJct = new double[length];
        }
    }

    private static class TripWorker implements Runnable {

        private final ConcurrentLinkedQueue<Integer> tripIndices;
        private final Counter counter;
        private final Vehicle vehicle;
        private final LeastCostPathCalculator[] lcpCalculators;
        private final int[] lcpCalculatorIdx;
        private final TravelTime tt;
        private final String mode;
        private final PathData pathData;


        public TripWorker(ConcurrentLinkedQueue<Integer> tripIndices, Counter counter, String mode, TravelTime travelTime, Vehicle vehicle,
                          LeastCostPathCalculator[] lcpCalculators, int[] lcpCalculatorIdx, PathData pathData) {
            this.tripIndices = tripIndices;
            this.counter = counter;
            this.mode = mode;
            this.tt = travelTime;
            this.vehicle = vehicle;
            this.lcpCalculators = lcpCalculators;
            this.lcpCalculatorIdx = lcpCalculatorIdx;
            this.pathData = pathData;
        }

        public void run() {

            while(true) {
                Integer i = this.tripIndices.poll();
                if(i == null) {
                    return;
                }
                //this.counter.incCounter();
                if(!pathData.intrazonal[i]) {
                    LeastCostPathCalculator.Path path = lcpCalculators[lcpCalculatorIdx[i]].calcLeastCostPath(pathData.originNodes[i], pathData.destinationNodes[i], 0., null, vehicle);
                    double tripGradient = 0.;
                    double tripVgvi = 0.;
                    double tripStressLink = 0.;
                    double tripStressJct = 0.;
                    for(Link link : path.links) {
                        double linkTime = tt.getLinkTravelTime(link,0.,null,vehicle) / 60;
                        tripGradient += linkTime * Math.max(Math.min(Gradient.getGradient(link),0.5),0.);
                        tripVgvi += linkTime * Math.max(0.,0.81 - LinkAmbience.getVgviFactor(link));
                        tripStressLink += linkTime * LinkStress.getStress(link,mode);
                        if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                            double linkLength = link.getLength();
                            double junctionWidth = Math.min(linkLength,(double) link.getAttributes().getAttribute("crossWidth"));
                            tripStressJct += linkTime * (junctionWidth / linkLength) * JctStress.getStress(link,mode);
                        }
                    }
                    pathData.time[i] = path.travelTime / 60;
                    pathData.grad[i] = tripGradient;
                    pathData.vgvi[i] = tripVgvi;
                    pathData.stressLink[i] = tripStressLink;
                    pathData.stressJct[i] = tripStressJct;
                }
            }
        }
    }

    private static void computeIntrazonalTripData(PathData pathData, Trip[] trips, AbstractUtilitySpecification u, Set<SimpleFeature> zones, Network net, String mode) {

        // Fix average speed (m/s)
        double averageSpeed;
        if(mode.equals("bike")) {
            averageSpeed = 5.1;
        } else if(mode.equals("walk")) {
            averageSpeed = 1.38;
        } else {
            throw new RuntimeException("Mode " + mode + " not supported!");
        }

        // Determine set of links for each zone
        Map<SimpleFeature, IdSet<Link>> linksPerFeature = GisUtils.calculateLinksIntersectingZones(zones,net);
        Map<String,IdSet<Link>> linksPerZone = linksPerFeature.entrySet().stream().collect(Collectors.toMap(e -> ((String) e.getKey().getAttribute("geo_code")), Map.Entry::getValue));

        // Identify intrazonal trips
        int intrazonalTripCount = 0;

        // Compute values for intrazonal trips
        for(int i = 0 ; i < trips.length ; i++) {
            if(trips[i].getZone(Place.ORIGIN).equals(trips[i].getZone(Place.DESTINATION))) {
                pathData.intrazonal[i] = true;
                intrazonalTripCount++;
                double tripLength = u.value(i,"dist");
                if(tripLength == 0) {
                    logger.warn("Zero trip distance for household " + trips[i].getHouseholdId() + " person " + trips[i].getPersonId() +
                            " trip " + trips[i].getTripId() + "! Setting to 50m");
                    tripLength = 50;
                }
                double totLength = 0;
                double totGrad = 0;
                double totVgvi = 0;
                double totStressLink = 0;
                double totStressJct = 0;
                for(Id<Link> linkId : linksPerZone.get(trips[i].getZone(Place.ORIGIN))) {
                    Link link = net.getLinks().get(linkId);
                    double linkLength = link.getLength();
                    totLength += linkLength;
                    totGrad += linkLength * Math.max(Math.min(Gradient.getGradient(link),0.5),0.);
                    totVgvi += linkLength * Math.max(0.,0.81 - LinkAmbience.getVgviFactor(link));
                    totStressLink += linkLength * LinkStress.getStress(link,mode);
                    if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                        double junctionWidth = Math.min(link.getLength(),(double) link.getAttributes().getAttribute("crossWidth"));
                        totStressJct += junctionWidth * JctStress.getStress(link,mode);
                    }
                }
                if(tripLength > totLength) {
                    logger.warn("Trip distance for household " + trips[i].getHouseholdId() + " person " + trips[i].getPersonId() + " trip " + trips[i].getTripId() +
                            " (" + tripLength + "m) exceeds total length of all links intersecting zone " + trips[i].getZone(Place.ORIGIN) + " (" + totLength +
                            "m) . Setting to total length.");
                    tripLength = totLength;
                }
                double tripTime = (tripLength / averageSpeed) / 60.;
                double adj = tripTime / totLength;
                pathData.time[i] = tripTime;
                pathData.grad[i] = totGrad * adj;
                pathData.vgvi[i] = totVgvi * adj;
                pathData.stressLink[i] = totStressLink * adj;
                pathData.stressJct[i] = totStressJct * adj;
            }
        }
        logger.info("Identified " + intrazonalTripCount + " intrazonal " + mode + " trips.");
    }
}
