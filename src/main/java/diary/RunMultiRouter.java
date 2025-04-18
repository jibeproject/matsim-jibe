package diary;

import estimation.RouteAttribute;
import gis.GpkgReader;
import io.ioUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.Bicycle;
import routing.Gradient;
import routing.disutility.JibeDisutility4;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import routing.travelTime.WalkTravelTime;
import diary.calculate.LogitDataCalculator;
import io.DiaryReader;
import trip.Trip;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class RunMultiRouter {

    private final static Logger logger = Logger.getLogger(RunMultiRouter.class);

    private final static int SAMPLES = 1000;

    private final static String SEP = ",";

    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Previous output csv file path \n" +
                    "(2) New output csv file path\n" +
                    "(3) Marginal costs file path\n" +
                    "(4) Mode");
        }

        Resources.initializeResources(args[0]);
        String inputCsv = args[1];
        String outputCsv = args[2];
        String mcCsv = args[3];
        String mode = args[4];

        // Random number
        Random random = new Random();

        // Read network
        Network network = NetworkUtils2.readModeSpecificNetwork(mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = DiaryReader.readTrips(boundary);

        // Filter to only routable trips with chosen mode
        Set<Trip> selectedTrips = trips.stream()
                .filter(t -> t.routable(ORIGIN, DESTINATION))
//                .filter(t -> ((t.getEndPurpose().isMandatory() && t.getStartPurpose().equals(Purpose.HOME)) ||
//                        (t.getStartPurpose().isMandatory() && t.getEndPurpose().equals(Purpose.HOME))))
                .collect(Collectors.toSet());

        // Travel time and vehicle
        TravelTime tt;
        Vehicle veh;

        if(mode.equals(TransportMode.bike)) {
            Bicycle bicycle = new Bicycle(null);
            tt = bicycle.getTravelTimeFast(network);
            veh = bicycle.getVehicle();
        } else if (mode.equals(TransportMode.walk)) {
            tt = new WalkTravelTime();
            veh = null;
        } else throw new RuntimeException("Modes other than walk and bike are not supported!");

        // Precalculate origin/destination nodes for each trip
        logger.info("Precalculating origin and destination nodes...");
        Iterator<Trip> it = selectedTrips.iterator();
        while(it.hasNext()) {
            Trip trip = it.next();
            Node origNode = network.getNodes().get(NetworkUtils.getNearestLinkExactly(network, trip.getCoord(ORIGIN)).getToNode().getId());
            Node destNode = network.getNodes().get(NetworkUtils.getNearestLinkExactly(network, trip.getCoord(DESTINATION)).getToNode().getId());
            if(origNode.equals(destNode)) {
                it.remove();
                logger.warn("HouseholdID " + trip.getHouseholdId() + " Person " + trip.getPersonId() + " Trip " + trip.getTripId() +
                        " are in different zones but have the same origin & destination. Removing...");
            } else {
                trip.setNodes(origNode,destNode);
            }
        }

        // READ PREVIOUS TRIPS
        int currPathCount = 0;
        if(inputCsv.endsWith(".csv")) {
            readPreviousPaths(selectedTrips,inputCsv);
            currPathCount = selectedTrips.stream().mapToInt(t -> t.getPaths().size()).sum();
            logger.info("Passed over " + currPathCount + " paths from previous run(s).");
        }

        // GENERATE RANDOM NUMBERS AND WRITE SAMPLES
        logger.info("Randomly sampling " + SAMPLES + " sets of marginal cost values.");
        double[] mcGradient = new double[SAMPLES]; //random.doubles(SAMPLES,0,100).toArray(); // 0-30 for cycling, 0-2 for walking
        double[] mcVgvi = random.doubles(SAMPLES,0,2).toArray();
        double[] mcStressLink = new double[SAMPLES]; //random.doubles(SAMPLES,0,10).toArray();
        double[] mcStressJct = random.doubles(SAMPLES,0,30).toArray();
        int[] newPathCount = new int[SAMPLES];

        // ESTIMATE PATHS
        logger.info("Estimating paths...");

        List<RouteAttribute> disutilityComponents = new ArrayList<>();
        disutilityComponents.add(new RouteAttribute("grad",l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.)));
        disutilityComponents.add(new RouteAttribute("vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
        disutilityComponents.add(new RouteAttribute("stressLink", l -> LinkStress.getStress(l,mode)));
        disutilityComponents.add(new RouteAttribute("stressJct", l -> JctStress.getStressProp(l,mode)));

        LogitDataCalculator calc = new LogitDataCalculator(selectedTrips);
        for (int i = 0 ; i < SAMPLES ; i++) {
            logger.info("Estimating path for sample " + (i+1) + "...");
            JibeDisutility4 disutility = new JibeDisutility4(network,veh,mode,tt,disutilityComponents,
                    new double[] {mcGradient[i],mcVgvi[i],mcStressLink[i],mcStressJct[i]});
            calc.calculate(veh,network,disutility,tt);
            int thisPathCount = selectedTrips.stream().mapToInt(t -> t.getPaths().size()).sum();
            newPathCount[i] = thisPathCount - currPathCount;
            currPathCount = thisPathCount;
            logger.info("Created " + newPathCount[i] + " new paths. Total path count = " + currPathCount);
        }


        // WRITE SAMPLE DATA
        logger.info("Writing randomly sampled marginal cost values to " + mcCsv);
        File mcCsvFile = new File(mcCsv);
        boolean writeHeader = !mcCsvFile.exists();
        PrintWriter out = ioUtils.openFileForSequentialWriting(mcCsvFile,true);
        assert out != null;
        if(writeHeader) {
            out.println("mcGradient" + SEP + "mcVgvi" + SEP + "mcStressLink" + SEP + "mcStressJct" + SEP + "newPathCount");
        }
        for(int i = 0 ; i < SAMPLES ; i++) {
            out.println(mcGradient[i] + SEP + mcVgvi[i] + SEP + mcStressLink[i] + SEP + mcStressJct[i] + SEP + newPathCount[i]);
        }
        out.close();

        // WRITE PATH RESULTS
        logger.info("Writing routes to " + outputCsv);
        out = ioUtils.openFileForSequentialWriting(new File(outputCsv), false);
        assert out != null;

        // Write header
        out.println("IDNumber" + SEP + "PersonNumber" + SEP + "TripNumber" + SEP + "path" + SEP +
                "distance" + SEP + "travelTime" + SEP + "gradient" + SEP + "vgvi" + SEP +
                "stressLink" + SEP + "stressJct" + SEP + "links");

        // Write routes
        for(Trip trip : selectedTrips) {
            int pathId = 0;
            for(List<Id<Link>> path : trip.getPaths()) {
                StringBuilder links = new StringBuilder();
                double distance = 0.;
                double travelTime = 0.;
                double gradient = 0.;
                double vgvi = 0.;
                double stressLink = 0.;
                double stressJct = 0.;
                for(Id<Link> linkId : path) {
                    Link link = network.getLinks().get(linkId);
                    links.append(linkId.toString()).append("-");
                    double linkLength = link.getLength();
                    double linkTime = tt.getLinkTravelTime(link,trip.getStartTime(),null,veh);
                    distance += linkLength;
                    travelTime += linkTime;
                    gradient += linkTime * Math.max(Math.min(Gradient.getGradient(link),0.5),0.);
                    vgvi += linkTime * Math.max(0.,0.81 - LinkAmbience.getVgviFactor(link));
                    stressLink += linkTime * LinkStress.getStress(link,mode);
                    if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                        double junctionWidth = Math.min(linkLength,(double) link.getAttributes().getAttribute("crossWidth"));
                        stressJct += linkTime * (junctionWidth / linkLength) * JctStress.getStress(link,mode);
                    }
                }
                links.deleteCharAt(links.length() - 1);
                out.println(trip.getHouseholdId() + SEP + trip.getPersonId() + SEP + trip.getTripId() + SEP + pathId + SEP +
                        distance + SEP + travelTime + SEP + gradient + SEP + vgvi + SEP + stressLink + SEP + stressJct + SEP +
                        links);
                pathId++;
            }
        }
        out.close();
    }

    private static void readPreviousPaths(Set<Trip> trips, String inputCsv) throws IOException {
        Counter counter = new Counter("Line-");
        BufferedReader in = new BufferedReader(new FileReader(inputCsv));
        in.readLine();

        String recString;
        while((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] lineElements = recString.split(SEP);
            String householdId = lineElements[0];
            int personId = Integer.parseInt(lineElements[1]);
            int tripId = Integer.parseInt(lineElements[2]);
            Trip trip = findTrip(trips,householdId,personId,tripId);

            String[] linkIds = lineElements[10].split("-");
            List<Id<Link>> path = new ArrayList<>();
            for (String linkId : linkIds) {
                path.add(Id.createLinkId(linkId));
            }
            trip.addPathFast(path);
        }
        in.close();
        logger.info("Read " + counter.getCounter() + " existing paths.");
    }

    private static Trip findTrip(Set<Trip> trips, String householdId, int personId, int tripId) {
        for(Trip trip : trips) {
            if(trip.getHouseholdId().equals(householdId)) {
                if(trip.getPersonId() == personId) {
                    if(trip.getTripId() == tripId) {
                        return trip;
                    }
                }
            }
        }
        throw new RuntimeException("Trip from CSV not found! HouseholdId: " + householdId + ", personId: " + personId + ", tripId: " + tripId);
    }
}
