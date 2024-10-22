package estimation.dynamic;

import estimation.RouteAttribute;
import estimation.specifications.AbstractModelSpecification;
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
import routing.disutility.JibeDisutility4;
import trip.Place;
import trip.Trip;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class RouteDataDynamic implements RouteData, DynamicComponent {

    private final static boolean ENABLE_DYNAMIC_ROUTING = true;
    private final static Logger logger = Logger.getLogger(RouteDataDynamic.class);
    private final int numberOfThreads;
    final int tripCount;
    final boolean[][] validity;
    private final List<boolean[]> personas;
    private final int[] personasIdx;
    final String mode;
    final Network network;
    final TravelTime tt;
    final AbstractModelSpecification u;
    final List<RouteAttribute> attributes;
    final List<RouteAttribute> baseAttributes;
    final int[] baseAttributeIdx;
    final int[] baseAttributeVal;
    final int[] attributeCoeffPositions;
    final Vehicle vehicle;
    final PathData pathData;
    final LeastCostPathCalculator.Path[] initialPath;

    String detourStats;
    String overlapStats;


    public RouteDataDynamic(String[] ids, Trip[] trips, AbstractModelSpecification u, String mode, Set<SimpleFeature> zones,
                            Network network, Vehicle vehicle, TravelTime tt,
                            List<RouteAttribute> attributes) {
        this.tripCount = ids.length;
        this.u = u;
        this.mode = mode;
        this.network = network;
        this.vehicle = vehicle;
        this.tt = tt;
        this.attributes = attributes;
        this.numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);

        // Get position of coefficients corresponding to attributes
        attributeCoeffPositions = new int[attributes.size()];

        // Initialise all...
        validity = new boolean[trips.length][attributes.size()];
        baseAttributes = new ArrayList<>();
        Map<String,Integer> baseAttributeNameIndex = new LinkedHashMap<>();
        int baseAttributeCount = (int) attributes.stream().filter(RouteAttribute::isBaseAttribute).count();
        baseAttributeVal = new int[baseAttributeCount];
        baseAttributeIdx = new int[attributes.size()];
        int k = 0;

        logger.info("STREET-LEVEL ENVIRONMENT ATTRIBUTES FOR MODE " + mode.toUpperCase() + ":");
        for(int i = 0 ; i < attributes.size() ; i++) {
            RouteAttribute attribute = attributes.get(i);

            // Get position of attribute in (full) coefficient vector
            attributeCoeffPositions[i] = u.getCoeffPos(attribute.getName());

            // Test valid records for each attribute
            int validityCount = 0;
            for(int j = 0 ; j < trips.length ; j++) {
                if(attribute.test(j)) {
                    validity[j][i] = true;
                    validityCount++;
                }
            }

            // Base or interaction attribute?
            String attributeType;
            if(attribute.isBaseAttribute()) {
                attributeType = "base";
                baseAttributes.add(attribute);
                baseAttributeNameIndex.put(attribute.getName(),k);
                baseAttributeVal[k] = i;
                baseAttributeIdx[i] = k;
                k++;
            } else {
                String correspondingBaseAttributeName = attribute.getCorrespondingBaseAttributeName();
                attributeType = "adjusts \"" + correspondingBaseAttributeName + "\"";
                Integer correspondingBaseAttributeIndex = baseAttributeNameIndex.get(correspondingBaseAttributeName);
                if(correspondingBaseAttributeIndex != null) {
                    baseAttributeIdx[i] = correspondingBaseAttributeIndex;
                } else {
                    throw new RuntimeException("Base attribute \"" + correspondingBaseAttributeName + "\" must be listed ahead of interaction attribute \"" + attribute.getName() + "\"!");
                }
            }

            // LOG ATTRIBUTE DETAILS
            String attributeDetails = "\"" + attribute.getName() + "\" (" + attributeType + ", valid for " + validityCount + "/" + trips.length + " trips).";
            String networkStats = "";
            if(attribute.isBaseAttribute()) {
                double[] values = network.getLinks().values().stream().mapToDouble(attribute::getValue).sorted().toArray();
                networkStats = " Network stats: " + getSummaryStats(values);
                if(values[0] < 0) {
                    throw new RuntimeException("Street-level environment attributes must always be >= 0");
                }
            }
            logger.info(attributeDetails + networkStats);
        }

        // Count and index personas
        personas = new ArrayList<>();
        personasIdx = new int[trips.length];
        Arrays.fill(personasIdx,-1);
        int currIdx = 0;
        for(int i = 0 ; i < trips.length ; i++) {
            for(int j = 0 ; j < i ; j++) {
                if(Arrays.equals(validity[i],validity[j])) {
                    personasIdx[i] = personasIdx[j];
                    break;
                }
            }
            if(personasIdx[i] == -1) {
                personas.add(Arrays.copyOf(validity[i],attributes.size()));
                personasIdx[i] = currIdx;
                currIdx++;
            }
        }

        // Print personas, total number of records, corresponding variables
        logger.info("IDENTIFIED " + currIdx + " UNIQUE PERSONAS:");
        Map<Integer, Long> counts = Arrays.stream(personasIdx).boxed().collect(Collectors.groupingBy(Function.identity(),Collectors.counting()));
        for(int i = 0 ; i < currIdx ; i++) {
            boolean[] persona = personas.get(i);
            String personaAttributes = IntStream.range(0,attributes.size()).filter(j -> persona[j]).mapToObj(j -> attributes.get(j).getName()).collect(Collectors.joining(", "));
            logger.info("Persona " + i + ", " + counts.get(i) + " records: " + personaAttributes);
        }

        // Sort inter/intra-zonal trips, and compute fixed results for intrazonal
        pathData = new PathData(trips.length);
        computeIntrazonalTripData(pathData,trips,u,zones,baseAttributes,network,mode);

        // Origin and destination nodes
        computeOriginAndDestinationNodes(pathData,trips,network);

        // use in debugger to check zero costs: IntStream.range(0,cost.length).filter(i->cost[i] == 0).toArray();
        // Compute costs for starting values
        logger.info("Running initial routing estimate...");
        updatePathData(u.getStarting());

        // Store initial path (check this works later)
        initialPath = new LeastCostPathCalculator.Path[trips.length];
        System.arraycopy(pathData.path,0,initialPath,0,trips.length);

        // Print results from initial path
        RouteDataIO.writeStaticData(ids, attributes,this,mode);
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

    public double getAttribute(int i, int j) {
        if(validity[i][j]) {
            return pathData.attributes[i][baseAttributeIdx[j]];
        } else {
            return 0.;
        }
    }

    public void update(double[] xVarOnly) {
        if(ENABLE_DYNAMIC_ROUTING && !attributes.isEmpty()) {
            updatePathData(xVarOnly);
            computeRouteStats();
        }
    }

    private void updatePathData(double[] xVarOnly) {

        // GET LATEST COEFFICIENTS
        double[] x = u.expandCoeffs(xVarOnly);

        TravelDisutility[] disutilitities = new TravelDisutility[personas.size()];
        for(int k = 0 ; k < personas.size() ; k++) {
            boolean[] persona = personas.get(k);
            double[] weights = new double[baseAttributes.size()];
            for(int i = 0 ; i < attributes.size() ; i++) {
                if (persona[i]) {
                    weights[baseAttributeIdx[i]] += x[attributeCoeffPositions[i]];
                }
            }
            for(int i = 0 ; i < weights.length ; i++) {
                if (weights[i] < 0) {
                    logger.info(mode.toUpperCase() + " PERSONA " + k + " weight for \"" + attributes.get(baseAttributeVal[i]).getName() + "\" = " + weights[i] + " < 0. Setting to 0 for routing...");
                    weights[i] = 0;
                }
            }
            disutilitities[k] = new JibeDisutility4(network, vehicle, mode, tt, baseAttributes, weights);
        }

        // Setup miltithreaded...
        Thread[] threads = new Thread[numberOfThreads];
        Counter counter = new Counter("Routed ", " / " + tripCount + " trips.");
        ConcurrentLinkedQueue<Integer> tripsQueue = IntStream.range(0, tripCount).boxed().collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
        for(int i = 0 ; i < numberOfThreads ; i++) {
            LeastCostPathCalculator[] lcpCalculators = new LeastCostPathCalculator[personas.size()];
            for(int j = 0 ; j < personas.size() ; j++) {
                lcpCalculators[j] = new FastDijkstraFactory(false).createPathCalculator(network,disutilitities[j],tt);
            }
            TripWorker worker = new TripWorker(tripsQueue,counter,tt,vehicle,lcpCalculators, personasIdx,baseAttributes,pathData);
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

    private void computeRouteStats() {
        long startTime = System.currentTimeMillis();

        // Detour vs Fastest path
        double[] detours = IntStream.range(0,tripCount).filter(i -> !(pathData.intrazonal[i]))
                .mapToDouble(i -> pathData.path[i].travelTime / initialPath[i].travelTime)
                .sorted().toArray();
        detourStats = mode.toUpperCase() + " DETOURS: " + getSummaryStats(detours);
        logger.info(detourStats);

        // Overlap vs Fastest path
        double[] overlap = IntStream.range(0,tripCount).filter(i -> !(pathData.intrazonal[i]))
                .mapToDouble(i -> pathData.path[i].links.stream().filter(initialPath[i].links::contains)
                        .mapToDouble(l -> tt.getLinkTravelTime(l,0.,null,null)).sum() / initialPath[i].travelTime)
                .sorted().toArray();
        overlapStats = mode.toUpperCase() + " OVERLAP: " + getSummaryStats(overlap);
        logger.info(overlapStats);

        // Runtime (for debugging only)
        long endTime= System.currentTimeMillis();
        double runtime = (endTime - startTime) / 1000.;
        logger.info(mode.toUpperCase() + " Route stats computation runtime (s) = " + runtime);
    }

    public String getStats() {
        computeRouteStats();
        return this.detourStats + "\n" + this.overlapStats;
    }

    private String getSummaryStats(double[] sortedArray) {
        return String.format("min=%.2f  25perc=%.2f  med=%.2f  mean=%.2f  75perc=%.2f  max=%.2f",
                sortedArray[0],
                sortedArray[sortedArray.length / 4],
                sortedArray[sortedArray.length / 2],
                Arrays.stream(sortedArray).average().orElseThrow(),
                sortedArray[3 * sortedArray.length / 4],
                sortedArray[sortedArray.length - 1]);
    }

    public static class PathData {
        final int length;
        final boolean[] intrazonal;
        final Node[] originNodes;
        final Node[] destinationNodes;
        final double[] time;
        final LeastCostPathCalculator.Path[] path;
        final double[][] attributes;

        public PathData(int length) {
            this.length = length;
            this.intrazonal = new boolean[length];
            this.originNodes = new Node[length];
            this.destinationNodes = new Node[length];
            this.time = new double[length];
            this.path = new LeastCostPathCalculator.Path[length];
            this.attributes = new double[length][];
        }
    }

    private static class TripWorker implements Runnable {

        private final ConcurrentLinkedQueue<Integer> tripIndices;
        private final Counter counter;
        private final Vehicle vehicle;
        private final LeastCostPathCalculator[] lcpCalculators;
        private final int[] lcpCalculatorIdx;
        private final TravelTime tt;
        private final List<RouteAttribute> attributes;
        private final PathData pathData;


        public TripWorker(ConcurrentLinkedQueue<Integer> tripIndices, Counter counter, TravelTime travelTime, Vehicle vehicle,
                          LeastCostPathCalculator[] lcpCalculators, int[] lcpCalculatorIdx, List<RouteAttribute> attributes, PathData pathData) {
            this.tripIndices = tripIndices;
            this.counter = counter;
            this.tt = travelTime;
            this.vehicle = vehicle;
            this.lcpCalculators = lcpCalculators;
            this.lcpCalculatorIdx = lcpCalculatorIdx;
            this.attributes = attributes;
            this.pathData = pathData;
        }

        public void run() {

            while(true) {
                Integer i = this.tripIndices.poll();
                if(i == null) {
                    return;
                }
//                this.counter.incCounter();
                if(!pathData.intrazonal[i]) {
                    LeastCostPathCalculator.Path path = lcpCalculators[lcpCalculatorIdx[i]].calcLeastCostPath(pathData.originNodes[i], pathData.destinationNodes[i], 0., null, vehicle);
                    double[] pathAttributes = new double[attributes.size()];
                    for(Link link : path.links) {
                        double linkTime = tt.getLinkTravelTime(link,0.,null,vehicle) / 60;
                        for(int j = 0 ; j < attributes.size() ; j++) {
                            pathAttributes[j] += linkTime * attributes.get(j).getValue(link);
                        }
                    }
                    pathData.path[i] = path;
                    pathData.time[i] = path.travelTime / 60;
                    pathData.attributes[i] = pathAttributes;
                }
            }
        }
    }

    private static void computeIntrazonalTripData(PathData pathData, Trip[] trips, AbstractModelSpecification u,
                                                  Set<SimpleFeature> zones, List<RouteAttribute> attributes,
                                                  Network net, String mode) {

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
        Map<String,IdSet<Link>> linksPerZone = linksPerFeature.entrySet().stream().collect(Collectors.toMap(e -> ((String) e.getKey().getAttribute("OA11CD")), Map.Entry::getValue));

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
                double[] totAttributes = new double[attributes.size()];
                IdSet<Link> linkIds = linksPerZone.get(trips[i].getZone(ORIGIN));
                if(linkIds == null) {
                    throw new RuntimeException("No zone data for intrazonal trip, household " + trips[i].getHouseholdId() +
                            " person " + trips[i].getPersonId() + " trip " + trips[i].getTripId());
                }
                for(Id<Link> linkId : linkIds) {
                    Link link = net.getLinks().get(linkId);
                    double linkLength = link.getLength();
                    totLength += linkLength;
                    for(int j = 0 ; j < attributes.size() ; j++) {
                        totAttributes[j] += linkLength * attributes.get(j).getValue(link);
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
                for(int j = 0 ; j < attributes.size() ; j++) {
                    totAttributes[j] *= adj;
                }
                pathData.time[i] = tripTime;
                pathData.attributes[i] = totAttributes;
            }
        }
        logger.info("Identified " + intrazonalTripCount + " intrazonal " + mode + " trips.");
    }
}
