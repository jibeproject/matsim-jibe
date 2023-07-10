package demand;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeature;

import omx.OmxFile;
import resources.Properties;
import resources.Resources;

import java.util.*;

public class GenerateTfGMPlans {
    private static final Logger logger = Logger.getLogger(GenerateTfGMPlans.class);

    private final Counter totalTrips =  new Counter("trips");

    private final Random rand;

    // Define objects and parameters
    private final Scenario scenario;

    private final String zonesFilepath;

    private final String omxFolder;
    private Map<Integer, Geometry> shapeMap;

    // Entering point of the class "Generate Random Demand"
    public static void main(String[] args) {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Zones file path \n" +
                    "(2) Folder containing relevant OMX files");
        }

        Resources.initializeResources(args[0]);
        String zonesFilepath = args[1];
        String omxFolder = args[2];

        GenerateTfGMPlans grd = new GenerateTfGMPlans(zonesFilepath,omxFolder);
        grd.run();
    }

    // A constructor for this class, which is to set up the scenario container.
    GenerateTfGMPlans(String zonesFilepath, String omxFolder) {
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.zonesFilepath = zonesFilepath;
        this.omxFolder = omxFolder;
        this.rand = new Random();
    }

    // Generate randomly sampling demand
    private void run() {

        // READ ZONES SHAPEFILE
        this.shapeMap = readShapeFile(zonesFilepath, "HW1075");

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
        OmxFile omx = new OmxFile(omxFolder + "M65Airport_time3.OMX");
        omx.openReadOnly();
        double[][] eveningUc1 = (double[][]) omx.getMatrix("L01_M65Airport_time3_uc1").getData();
        double[][] eveningUc2 = (double[][]) omx.getMatrix("L02_M65Airport_time3_uc2").getData();
        double[][] eveningUc3 = (double[][]) omx.getMatrix("L03_M65Airport_time3_uc3").getData();
        double[][] eveningUc4 = (double[][]) omx.getMatrix("L04_M65Airport_time3_uc4").getData();
        double[][] eveningUc5 = (double[][]) omx.getMatrix("L05_M65Airport_time3_uc5").getData();
        omx.close();        

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

                TimeSampler ipTimeSampler = new InterpeakTimeSampler();
                createOD(interpeakUc1[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc2[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc3[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc4[i][j] * 9.159,"car",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(interpeakUc5[i][j] * 9.159 / 2.5,"truck",ipTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);

                TimeSampler eveningTimeSampler = new EveningTimeSampler();
                createOD(eveningUc1[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc2[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc3[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc4[i][j] * 2.75,"car",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
                createOD(eveningUc5[i][j] * 2.75 / 2.5,"truck",eveningTimeSampler,origZoneId,destZoneId,origZoneId + "_" + destZoneId);
            }
        }

        // Write the population file to specified folder
        PopulationWriter pw = new PopulationWriter(scenario.getPopulation(), scenario.getNetwork());
        pw.write(Resources.instance.getString(Properties.MATSIM_TFGM_PLANS));

        logger.info("Total commuter plans written: " + totalTrips);
    }

    // Read in shapefile
    public static Map<Integer, Geometry> readShapeFile(String filename, String attrString) {
        Map<Integer, Geometry> shapeMap = new HashMap<>();
        for (SimpleFeature ft : ShapeFileReader.getAllFeatures(filename)) {
            GeometryFactory geometryFactory = new GeometryFactory();
            WKTReader wktReader = new WKTReader(geometryFactory);
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
                return (7 + rand.nextDouble()) * 3600;
            } else if (r < 1.58/2.58) {
                return (9 + rand.nextDouble()) * 3600;
            } else {
                return (8 + rand.nextDouble()) * 3600;
            }
        }
    }

    public class EveningTimeSampler implements TimeSampler {
        public double sample() {
            double r = rand.nextDouble();
            if (r < 0.875/2.75) {
                return (16 + rand.nextDouble()) * 3600;
            } else if (r < 1.75/2.75) {
                return (18 + rand.nextDouble()) * 3600;
            } else {
                return (17 + rand.nextDouble()) * 3600;
            }
        }
    }

    public class InterpeakTimeSampler implements TimeSampler {
        public double sample() {
            double r = rand.nextDouble();
            if (r < 6/9.159) {
                return (10 + rand.nextDouble() * 6) * 3600;
            } else {
                return ((19 + rand.nextDouble() * 12) * 3600) % 86400;
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

    // Create od relations for each MSOA pair
    private void createOD(double pop,String mode,TimeSampler timeSampler,  int origin, int destination, String toFromPrefix) {

        int popInt = (int) pop;
        double remainder = pop - popInt;

        // Specify the ID of these two MSOAs
        Geometry origGeom = this.shapeMap.get(origin);
        Geometry destGeom = this.shapeMap.get(destination);

        if(origGeom != null && destGeom != null) {
            int i = 0;
            while (i < popInt) {
                createOnePerson(mode, timeSampler.sample(), drawRandomPointFromGeometry(origGeom), drawRandomPointFromGeometry(destGeom), toFromPrefix);
                i++;
            }
            if(rand.nextDouble() <= remainder) {
                createOnePerson(mode, timeSampler.sample(), drawRandomPointFromGeometry(origGeom), drawRandomPointFromGeometry(destGeom), toFromPrefix);
            }
        }
    }

    // Create plan for each commuter
    private void createOnePerson( String mode, double departureTime, Coord origCoord, Coord destCoord, String toFromPrefix) {

        totalTrips.incCounter();

        Id<Person> personId = Id.createPersonId(toFromPrefix + "_" + totalTrips.getCounter());
        Person person = scenario.getPopulation().getFactory().createPerson(personId);

        Plan plan = scenario.getPopulation().getFactory().createPlan();

        Activity origin = scenario.getPopulation().getFactory().createActivityFromCoord("loc", origCoord);
        origin.setEndTime(departureTime);
        plan.addActivity(origin);

        Leg leg = scenario.getPopulation().getFactory().createLeg(mode);
        plan.addLeg(leg);

        Activity destination = scenario.getPopulation().getFactory().createActivityFromCoord("loc", destCoord);
        plan.addActivity(destination);

        person.addPlan(plan);
        scenario.getPopulation().addPerson(person);
    }

}