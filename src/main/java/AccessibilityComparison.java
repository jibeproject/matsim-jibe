import ch.sbb.matsim.analysis.CalculateData;
import ch.sbb.matsim.analysis.Impedance;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import routing.disutility.JibeDisutility;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.WalkTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;

import java.io.IOException;

// Currently considers cycling accessibility only
public class AccessibilityComparison {

    private final static Logger log = Logger.getLogger(RouteComparison.class);
    private final static double MAX_BIKE_SPEED = 16 / 3.6;
    private final static double MARGINAL_COST_TIME = 2 / 300.; // seconds
    private final static double MARGINAL_COST_DISTANCE = 0.; // metres
    private final static double MARGINAL_COST_GRADIENT = 0.02; // m/100m
    private final static double MARGINAL_COST_SURFACE = 2e-4;
    private final static double MARGINAL_COST_ATTRACTIVENESS = 4e-3;
    private final static double MARGINAL_COST_STRESS = 8e-3;
    private final static double JUNCTION_EQUIVALENT_LENGTH = 10.; // in meters
    private final static String MODE = TransportMode.bike;

    public static void main(String[] args) throws IOException {
        if(args.length != 6) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Network File Path \n" +
                    "(1) Zone coordinates File \n" +
                    "(2) Zone weights file \n" +
                    "(3) Output File Path \n" +
                    "(4) Number of Threads \n");
        }

        String networkPath = args[0];
        String zoneCoordinatesFile = args[1];
        String zoneWeightsFile = args[2];
        String outputDirectory = args[3];
        int numberOfThreads = Integer.parseInt(args[4]);

        // Set up config
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode("bike");
        config.addModule(bicycleConfigGroup);

        // Set up calculator
        CalculateData calc = new CalculateData(outputDirectory,numberOfThreads, null);
        calc.loadSamplingPointsFromFile(zoneCoordinatesFile);

        // CREATE VEHICLE & SET UP TRAVEL TIME
        Vehicle veh;
        TravelTime tt;
        if(MODE.equals(TransportMode.walk)) {
            veh = null;
            tt = new WalkTravelTime();
        } else if (MODE.equals(TransportMode.bike)) {
            VehicleType type = VehicleUtils.createVehicleType(Id.create("routing", VehicleType.class));
            type.setMaximumVelocity(MAX_BIKE_SPEED);
            BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
            veh = VehicleUtils.createVehicle(Id.createVehicleId(1), type);
            tt = new BicycleTravelTime(linkSpeedCalculator);
        } else {
            throw new RuntimeException("Routing not set up for mode " + MODE);
        }

        // Modify marginal cost of gradient/surface if walk
        double marginalCostOfGradient = MARGINAL_COST_GRADIENT;
        double marginalCostOfSurfaceComfort = MARGINAL_COST_SURFACE;

        if(MODE.equals(TransportMode.walk)) {
            marginalCostOfGradient /= 2;
            marginalCostOfSurfaceComfort = 0;
        }

        log.info("Marginal cost of time (s): " + MARGINAL_COST_TIME);
        log.info("Marginal cost of distance (m): " + MARGINAL_COST_DISTANCE);
        log.info("Marginal cost of gradient (m/100m): " + MARGINAL_COST_GRADIENT);
        log.info("Marginal cost of surface comfort (m): " + MARGINAL_COST_SURFACE);

        // Set up impedance
        TravelDisutility td = new JibeDisutility(MODE,tt,MARGINAL_COST_TIME,MARGINAL_COST_DISTANCE,
                marginalCostOfGradient,marginalCostOfSurfaceComfort,MARGINAL_COST_ATTRACTIVENESS,MARGINAL_COST_STRESS,
                MARGINAL_COST_STRESS*JUNCTION_EQUIVALENT_LENGTH);

        // Set zone weights (from weights file)
        calc.loadZoneWeightsFromFile(zoneWeightsFile);

        // Set zone weights (set all equal)
        calc.setZoneWeightsToValue(1.);

        Impedance walkImpedance = c -> Math.exp(-0.001 * c);
        Impedance bikeImpedance = c -> Math.exp(-0.00007692776 * c);

//        calc.calculateAccessibilities(networkPath, config, "accessibilities",
//                tt,td,travelAttributes, walkImpedance, TransportMode.walk,l -> true);
    }



}
