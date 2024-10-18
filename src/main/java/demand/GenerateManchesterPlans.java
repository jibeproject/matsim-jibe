package demand;
import gis.GisUtils;
import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeature;

import omx.OmxFile;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.IOException;
import java.util.*;

public class GenerateManchesterPlans {
    private static final Logger logger = Logger.getLogger(GenerateManchesterPlans.class);

    private static final Set<String> ENTRY_LINKS = Set.of("79028rtn","224795out","349037out","293282rtn","298027out","468552out",
            "431466rtn","314184rtn","59994out","216963rtn","783rtn","457702out","128831out","448839rtn","103583out",
            "273137out","124103out","36650out","316205out","8582out","4083out","419out","74135out","205113out","292279rtn","31452out",
            "308490out","8823out","13111out","119034out","409269out","38024out","58867out");

    private static final Set<String> EXIT_LINKS = Set.of("79028out","227825out","349037rtn","293282out","164749out","175057out",
            "431466out","314184out","59994rtn","216963out","783out","457702rtn","220563out","448839out","103583rtn",
            "367168out","124102out","36650rtn","310600out","81480out","4084out","224706out","74136out","205113rtn","292279out",
            "308490rtn","206836out","349287out","119034rtn","409267out","38024rtn","58003out");

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final Counter totalTrips =  new Counter("trip_");
    private double unassignedTrips = 0.;

    private final Random rand;

    // Define objects and parameters
    private final Scenario scenario;
    private final String zonesFilepath;
    private final TimeDistributions timeDistribution;
    private final String omxFolder;
    private Map<Integer, Geometry> shapeMap;
    private Geometry regionBoundary;
    private Geometry networkBoundary;
    private LeastCostPathCalculator lcpCalculator;

    private Network entryNetwork;
    private Network exitNetwork;
    private Network internalNetwork;
    private final double sampleSize;

    // Entering point of the class "Generate Random Demand"
    public static void main(String[] args) throws FactoryException, IOException {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Zones file path \n" +
                    "(2) Folder containing relevant OMX files \n");
        }

        Resources.initializeResources(args[0]);
        String zonesFilepath = args[1];
        String omxFolder = args[2];

        GenerateManchesterPlans grd = new GenerateManchesterPlans(zonesFilepath,omxFolder);
        grd.run();
    }

    // A constructor for this class, which is to set up the scenario container.
    GenerateManchesterPlans(String zonesFilepath, String omxFolder) throws IOException {
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.zonesFilepath = zonesFilepath;
        this.omxFolder = omxFolder;
        this.rand = new Random();
        this.timeDistribution = new TimeDistributions(omxFolder + "vehicleEnRouteTimes.txt");
        this.sampleSize = Resources.instance.getDouble(Properties.MATSIM_DEMAND_SCALE_FACTOR);
    }

    // Generate randomly sampling demand
    private void run() throws IOException {

        // READ NETWORK BOUNDARY
        this.regionBoundary = GpkgReader.readRegionBoundary();
        this.networkBoundary = GpkgReader.readNetworkBoundary();

        // READ ZONES SHAPEFILE
        this.shapeMap = readShapeFile(zonesFilepath, "HW1075");

        // READ NETWORK
        Network vehicleNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(vehicleNetwork).readFile(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));

        // Create relevant networks
        this.internalNetwork = NetworkUtils2.extractXy2LinksNetwork(vehicleNetwork,l -> !((boolean) l.getAttributes().getAttribute("motorway")));
        this.entryNetwork = NetworkUtils2.extractXy2LinksNetwork(vehicleNetwork,l -> ENTRY_LINKS.contains(l.getId().toString()));
        this.exitNetwork = NetworkUtils2.extractXy2LinksNetwork(vehicleNetwork,l -> EXIT_LINKS.contains(l.getId().toString()));

        // Create dijkstra for car network
        Config config = ConfigUtils.createConfig();
        FreespeedTravelTimeAndDisutility freespeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        this.lcpCalculator = new FastDijkstraFactory(false).createPathCalculator(vehicleNetwork, freespeed, freespeed);

//        // For debugging
//        WriteNetworkGpkgSimple.write(this.internalNetwork,"internalNetwork.gpkg");
//        WriteNetworkGpkgSimple.write(this.entryNetwork,"entryNetwork.gpkg");
//        WriteNetworkGpkgSimple.write(this.exitNetwork,"exitNetwork.gpkg");

        // READ ALL OMX FILES (Takes around 30s per matrix ~ 8min total)
        logger.info("Reading Morning File");
        OmxFile omx1 = new OmxFile(omxFolder + "M65Airport_time1.OMX");
        omx1.openReadOnly();
        double[][] morningUc1 = (double[][]) omx1.getMatrix("L01_M65Airport_time1_uc1").getData();
        double[][] morningUc2 = (double[][]) omx1.getMatrix("L02_M65Airport_time1_uc2").getData();
        double[][] morningUc3 = (double[][]) omx1.getMatrix("L03_M65Airport_time1_uc3").getData();
        double[][] morningUc4 = (double[][]) omx1.getMatrix("L04_M65Airport_time1_uc4").getData();
        double[][] morningUc5 = (double[][]) omx1.getMatrix("L05_M65Airport_time1_uc5").getData();
        omx1.close();

        logger.info("Reading Interpeak File");
        OmxFile omx2 = new OmxFile(omxFolder + "M65Airport_time2.OMX");
        omx2.openReadOnly();
        double[][] interpeakUc1 = (double[][]) omx2.getMatrix("L01_M65Airport_time2_uc1").getData();
        double[][] interpeakUc2 = (double[][]) omx2.getMatrix("L02_M65Airport_time2_uc2").getData();
        double[][] interpeakUc3 = (double[][]) omx2.getMatrix("L03_M65Airport_time2_uc3").getData();
        double[][] interpeakUc4 = (double[][]) omx2.getMatrix("L04_M65Airport_time2_uc4").getData();
        double[][] interpeakUc5 = (double[][]) omx2.getMatrix("L05_M65Airport_time2_uc5").getData();
        omx2.close();

        logger.info("Reading Evening File");
        OmxFile omx3 = new OmxFile(omxFolder + "M65Airport_time3.OMX");
        omx3.openReadOnly();
        double[][] eveningUc1 = (double[][]) omx3.getMatrix("L01_M65Airport_time3_uc1").getData();
        double[][] eveningUc2 = (double[][]) omx3.getMatrix("L02_M65Airport_time3_uc2").getData();
        double[][] eveningUc3 = (double[][]) omx3.getMatrix("L03_M65Airport_time3_uc3").getData();
        double[][] eveningUc4 = (double[][]) omx3.getMatrix("L04_M65Airport_time3_uc4").getData();
        double[][] eveningUc5 = (double[][]) omx3.getMatrix("L05_M65Airport_time3_uc5").getData();
        omx3.close();

        for(int i = 0 ; i < 1075 ; i++) {
            for(int j = 0 ; j < 1075 ; j++) {
                int origZoneId = i+1;
                int destZoneId = j+1;
                TimeSampler morningTimeSampler = new MorningTimeSampler();
                createOD(morningUc1[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,true);
                createOD(morningUc2[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
                createOD(morningUc3[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,true);
                createOD(morningUc4[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
                createOD(morningUc5[i][j] * 2.58 / 2.5,"truck",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
//                createOD(morningUc5[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);

                TimeSampler ipTimeSampler = new InterpeakTimeSampler();
                createOD(interpeakUc1[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,true);
                createOD(interpeakUc2[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
                createOD(interpeakUc3[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,true);
                createOD(interpeakUc4[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
                createOD(interpeakUc5[i][j] * 9.159 / 2.5,"truck",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
//                createOD(interpeakUc5[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);

                TimeSampler eveningTimeSampler = new EveningTimeSampler();
                createOD(eveningUc1[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,true);
                createOD(eveningUc2[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
                createOD(eveningUc3[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,true);
                createOD(eveningUc4[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
                createOD(eveningUc5[i][j] * 2.75 / 2.5,"truck",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId,false);
//                createOD(eveningUc5[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId
            }
        }

        // Write the population file to specified folder
        PopulationWriter pw = new PopulationWriter(scenario.getPopulation(), scenario.getNetwork());
        pw.write(Resources.instance.getString(Properties.MATSIM_DEMAND_PLANS));

        logger.info("Total commuter plans written: " + totalTrips.getCounter());
        logger.info("Total unassigned trips: " + this.unassignedTrips);
    }

    // Read in shapefile
    public static Map<Integer, Geometry> readShapeFile(String filename, String attrString) {
        Map<Integer, Geometry> shapeMap = new HashMap<>();
        for (SimpleFeature ft : ShapeFileReader.getAllFeatures(filename)) {
            WKTReader wktReader = new WKTReader(GEOMETRY_FACTORY);
            Geometry geometry;
            try {
                geometry = wktReader.read((ft.getAttribute("the_geom")).toString());
                shapeMap.put((int) ft.getAttribute(attrString), geometry);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return shapeMap;
    }

    public interface TimeSampler {
        double sample();
    }

    public class MorningTimeSampler implements TimeSampler {
        public double sample() {
            double r = rand.nextDouble();
            if(r < 0.79/2.58) {
                return timeDistribution.sample(7);
            } else if (r < 1.58/2.58) {
                return timeDistribution.sample(9);
            } else {
                return timeDistribution.sample(8);
            }
        }
    }

    public class EveningTimeSampler implements TimeSampler {
        public double sample() {
            double r = rand.nextDouble();
            if (r < 0.875/2.75) {
                return timeDistribution.sample(16);
            } else if (r < 1.75/2.75) {
                return timeDistribution.sample(18);
            } else {
                return timeDistribution.sample(17);
            }
        }
    }

    public class InterpeakTimeSampler implements TimeSampler {
        public double sample() {
            double r = rand.nextDouble();
            if (r < 6/9.159) {
                return timeDistribution.sample(10);
            } else {
                return timeDistribution.sample(19);
            }
        }
    }

    // Create od relations for each MSOA pair
    private void createOD(double pop,String mode,TimeSampler timeSampler,  int origin, int destination, String toFromPrefix, boolean externalOnly) {

        if(pop > 0.) {
            int popInt = (int) pop;
            double remainder = pop - popInt;

            // Get zone polygons
            Geometry origZonePolygon = this.shapeMap.get(origin);
            Geometry destZonePolygon = this.shapeMap.get(destination);

            int i = 0;
            if (origZonePolygon != null && destZonePolygon != null) {
                Point origPoint = GisUtils.drawRandomPointFromGeometry(origZonePolygon);
                Point destPoint = GisUtils.drawRandomPointFromGeometry(destZonePolygon);
                while (i < popInt) {
                    if (rand.nextDouble() < this.sampleSize) {
                        if(!externalOnly || !regionBoundary.contains(origPoint) || !regionBoundary.contains(destPoint)) {
                            createOnePerson(mode, timeSampler.sample(), origPoint, destPoint, toFromPrefix);
                        }
                    }
                    i++;
                }
                if (rand.nextDouble() <= remainder) {
                    if (rand.nextDouble() < this.sampleSize) {
                        if(!externalOnly || !regionBoundary.contains(origPoint) || !regionBoundary.contains(destPoint)) {
                            createOnePerson(mode, timeSampler.sample(), origPoint, destPoint, toFromPrefix);
                        }
                    }
                }
            } else {
//                logger.warn("No shape found for origin " + origin + " and/or destination " + destination + ", with " + pop + " trips.");
                this.unassignedTrips += pop;
            }
        }
    }

    // Create plan for each commuter
    private void createOnePerson( String mode, double time, Point origPoint, Point destPoint, String toFromPrefix) {

        totalTrips.incCounter();

        Id<Person> personId = Id.createPersonId(toFromPrefix + "_" + totalTrips.getCounter());
        Person person = scenario.getPopulation().getFactory().createPerson(personId);

        Plan plan = scenario.getPopulation().getFactory().createPlan();

        // Convert points to coords
        Coord origCoord = new Coord(origPoint.getX(),origPoint.getY());
        Coord destCoord = new Coord(destPoint.getX(),destPoint.getY());

        // Create activities
        Activity origin = scenario.getPopulation().getFactory().createActivityFromCoord("loc", origCoord);
        Activity destination = scenario.getPopulation().getFactory().createActivityFromCoord("loc", destCoord);

        // Origin Link
        Link originLink;
        if (networkBoundary.contains(origPoint)) {
            originLink = NetworkUtils.getNearestLinkExactly(internalNetwork, origCoord);
        } else {
            originLink = NetworkUtils.getNearestLinkExactly(entryNetwork, origCoord);
        }
        origin.setLinkId(originLink.getId());

        // Destination link
        Link destinationLink;
        if (networkBoundary.contains(destPoint)) {
            destinationLink = NetworkUtils.getNearestLinkExactly(internalNetwork, destCoord);
        } else {
            destinationLink = NetworkUtils.getNearestLinkExactly(exitNetwork, destCoord);
        }
        destination.setLinkId(destinationLink.getId());

        // Calculate departure time
        Node fromNode = originLink.getFromNode();
        Node toNode = destinationLink.getToNode();
        double adjustment = lcpCalculator.calcLeastCostPath(fromNode,toNode,0.,null,null).travelTime / 2;
        double tripStartTime = time - adjustment;
        while(tripStartTime < 0) tripStartTime += 86400;
        origin.setEndTime(tripStartTime);

        // Leg
        Leg leg = scenario.getPopulation().getFactory().createLeg(mode);

        // Compile plan
        plan.addActivity(origin);
        plan.addLeg(leg);
        plan.addActivity(destination);

        // Add plan to person
        person.addPlan(plan);
        person.getAttributes().putAttribute("adjustment",adjustment);

        // Add person to population
        scenario.getPopulation().addPerson(person);
    }
}