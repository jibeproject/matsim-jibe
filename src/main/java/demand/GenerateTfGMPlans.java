package demand;
import gis.GpkgReader;
import network.NetworkUtils2;
import network.WriteNetworkGpkgSimple;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeature;

import omx.OmxFile;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.IOException;
import java.util.*;

public class GenerateTfGMPlans {
    private static final Logger logger = Logger.getLogger(GenerateTfGMPlans.class);

    private static final Set<String> ENTRY_LINKS = Set.of("79028rtn","224795out","349037out","293282rtn","298027out","468552out",
            "431466rtn","314184rtn","59994out","216963rtn","783rtn","457702out","128831out","448839rtn","103583out",
            "273137out","124103out","36650out","316205out","8582out","4083out","419out","74135out","205113out","292279rtn","31452out",
            "308490out","8823out","13111out","119034out","409269out","38024out","58867out");

    private static final Set<String> EXIT_LINKS = Set.of("79028out","227825out","349037rtn","293282out","164749out","175057out",
            "431466out","314184out","59994rtn","216963out","783out","457702rtn","220563out","448839out","103583rtn",
            "367168out","124102out","36650rtn","310600out","81480out","4084out","224706out","74136out","205113rtn","292279out",
            "308490rtn","206836out","349287out","119034rtn","409267out","38024rtn","58003out");

    private static final List<String> PAIRS_TO_CONNECT = List.of("227825out","224795out","164749out","298027out",
            "220563out","128831out","367168out","273137out","124102out","124103out","81480out","8582out","4084out","4083out",
            "224706out","419out","206836out","8823out","349287out","13111out","409267out","409269out","58003out","58867out");

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final Counter totalTrips =  new Counter("trip_");

    private final Random rand;

    // Define objects and parameters
    private final Scenario scenario;
    private final String zonesFilepath;
    private final TimeDistributions timeDistribution;
    private final String omxFolder;
    private Map<Integer, Geometry> shapeMap;

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

        GenerateTfGMPlans grd = new GenerateTfGMPlans(zonesFilepath,omxFolder);
        grd.run();
    }

    // A constructor for this class, which is to set up the scenario container.
    GenerateTfGMPlans(String zonesFilepath, String omxFolder) throws IOException {
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.zonesFilepath = zonesFilepath;
        this.omxFolder = omxFolder;
        this.rand = new Random();
        this.timeDistribution = new TimeDistributions(omxFolder + "vehicleEnRouteTimes.txt");
        this.sampleSize = Resources.instance.getDouble(Properties.MATSIM_TFGM_SCALE_FACTOR);
    }

    // Generate randomly sampling demand
    private void run() throws IOException {

        // READ NETWORK BOUNDARY
        this.networkBoundary = GpkgReader.readNetworkBoundary();

        // READ ZONES SHAPEFILE
        this.shapeMap = readShapeFile(zonesFilepath, "HW1075");

        // READ NETWORK
        Network fullNetwork = NetworkUtils2.readFullNetwork();
        Network carNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(fullNetwork).filter(carNetwork, Set.of(TransportMode.car,TransportMode.truck));
        createConnectors(carNetwork);
        NetworkUtils.runNetworkCleaner(carNetwork);
        new NetworkWriter(carNetwork).write(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));

        // Create relevant networks
        this.internalNetwork = NetworkUtils2.extractXy2LinksNetwork(carNetwork,l -> !((boolean) l.getAttributes().getAttribute("motorway")));
        this.entryNetwork = NetworkUtils2.extractXy2LinksNetwork(carNetwork,l -> ENTRY_LINKS.contains(l.getId().toString()));
        this.exitNetwork = NetworkUtils2.extractXy2LinksNetwork(carNetwork,l -> EXIT_LINKS.contains(l.getId().toString()));

        // Create dijkstra for car network
        Config config = ConfigUtils.createConfig();
        FreespeedTravelTimeAndDisutility freespeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        this.lcpCalculator = new FastDijkstraFactory(false).createPathCalculator(carNetwork, freespeed, freespeed);

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
                createOD(morningUc1[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(morningUc2[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(morningUc3[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(morningUc4[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(morningUc5[i][j] * 2.58 / 2.5,"truck",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
//                createOD(morningUc5[i][j] * 2.58,"car",morningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);

                TimeSampler ipTimeSampler = new InterpeakTimeSampler();
                createOD(interpeakUc1[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc2[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc3[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc4[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc5[i][j] * 9.159 / 2.5,"truck",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
//                createOD(interpeakUc5[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);

                TimeSampler eveningTimeSampler = new EveningTimeSampler();
                createOD(eveningUc1[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc2[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc3[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc4[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc5[i][j] * 2.75 / 2.5,"truck",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
//                createOD(eveningUc5[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId
            }
        }

        // Write the population file to specified folder
        PopulationWriter pw = new PopulationWriter(scenario.getPopulation(), scenario.getNetwork());
        pw.write(Resources.instance.getString(Properties.MATSIM_TFGM_PLANS));

        logger.info("Total commuter plans written: " + totalTrips.getCounter());
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

    // Create random coordinates within a given polygon
    private Coord drawRandomPointFromGeometry(Geometry g) {
        Random rnd = MatsimRandom.getLocalInstance();
        Point p;
        double x, y;
        do {
            x = g.getEnvelopeInternal().getMinX()
                    + rnd.nextDouble() * (g.getEnvelopeInternal().getMaxX() - g.getEnvelopeInternal().getMinX());
            y = g.getEnvelopeInternal().getMinY()
                    + rnd.nextDouble() * (g.getEnvelopeInternal().getMaxY() - g.getEnvelopeInternal().getMinY());
            p = MGC.xy2Point(x, y);
        } while (!g.contains(p));
        return new Coord(p.getX(), p.getY());
    }

    private void createConnectors(Network net) {
        NetworkFactory fac = net.getFactory();

        for(int i = 0 ; i < PAIRS_TO_CONNECT.size() ; i += 2) {
            Link linkOut = net.getLinks().get(Id.createLinkId(PAIRS_TO_CONNECT.get(i)));
            Link linkIn = net.getLinks().get(Id.createLinkId(PAIRS_TO_CONNECT.get(i+1)));

            Node fromNode = linkOut.getToNode();
            Node toNode = linkIn.getFromNode();

            Link connector = fac.createLink(Id.createLinkId(PAIRS_TO_CONNECT.get(i) + "_" + PAIRS_TO_CONNECT.get(i+1)),linkOut.getToNode(),linkIn.getFromNode());
            connector.setLength(NetworkUtils.getEuclideanDistance(fromNode.getCoord(),toNode.getCoord()));
            connector.setFreespeed(Math.max(linkIn.getFreespeed(),linkOut.getFreespeed()));
            connector.setCapacity(Math.max(linkIn.getCapacity(),linkOut.getCapacity()));
            connector.setNumberOfLanes(Math.max(linkIn.getNumberOfLanes(),linkOut.getNumberOfLanes()));
            connector.getAttributes().putAttribute("motorway",true);
            connector.getAttributes().putAttribute("trunk",true);
            connector.getAttributes().putAttribute("fwd",true);
            connector.getAttributes().putAttribute("edgeID","connector");
            connector.setAllowedModes(Set.of(TransportMode.car,TransportMode.truck));

            net.addLink(connector);
        }
    }

    // Create od relations for each MSOA pair
    private void createOD(double pop,String mode,TimeSampler timeSampler,  int origin, int destination, String toFromPrefix) {

        int popInt = (int) pop;
        double remainder = pop - popInt;

        // Specify the ID of these two MSOAs
        Geometry origGeom = this.shapeMap.get(origin);
        Geometry destGeom = this.shapeMap.get(destination);

        int i = 0;
        while (i < popInt) {
            if(rand.nextDouble() < this.sampleSize) {
                createOnePerson(mode, timeSampler.sample(), drawRandomPointFromGeometry(origGeom), drawRandomPointFromGeometry(destGeom), toFromPrefix);
            }
            i++;
        }
        if(rand.nextDouble() <= remainder) {
            if(rand.nextDouble() < this.sampleSize) {
                createOnePerson(mode, timeSampler.sample(), drawRandomPointFromGeometry(origGeom), drawRandomPointFromGeometry(destGeom), toFromPrefix);
            }
        }
    }

    // Create plan for each commuter
    private void createOnePerson( String mode, double time, Coord origCoord, Coord destCoord, String toFromPrefix) {

        totalTrips.incCounter();

        Id<Person> personId = Id.createPersonId(toFromPrefix + "_" + totalTrips.getCounter());
        Person person = scenario.getPopulation().getFactory().createPerson(personId);

        Plan plan = scenario.getPopulation().getFactory().createPlan();

        // Create activities
        Activity origin = scenario.getPopulation().getFactory().createActivityFromCoord("loc", origCoord);
        Activity destination = scenario.getPopulation().getFactory().createActivityFromCoord("loc", destCoord);

        // Origin Link
        Link originLink;
        if (networkBoundary.contains(GEOMETRY_FACTORY.createPoint(new Coordinate(origCoord.getX(), origCoord.getY())))) {
            originLink = NetworkUtils.getNearestLinkExactly(internalNetwork, origCoord);
        } else {
            originLink = NetworkUtils.getNearestLinkExactly(entryNetwork, origCoord);
        }
        origin.setLinkId(originLink.getId());

        // Destination link
        Link destinationLink;
        if (networkBoundary.contains(GEOMETRY_FACTORY.createPoint(new Coordinate(destCoord.getX(), destCoord.getY())))) {
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