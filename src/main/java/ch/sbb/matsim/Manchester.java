package ch.sbb.matsim;

import bicycle.BicycleLinkSpeedCalculatorDefaultImpl;
import bicycle.BicycleTravelDisutility;
import bicycle.BicycleTravelTime;
import ch.sbb.matsim.analysis.skims.CalculateData;
import ch.sbb.matsim.analysis.skims.TravelAttribute;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.contrib.bicycle.*;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;

import java.io.IOException;

public class Manchester {

    private final static Logger log = Logger.getLogger(Manchester.class);

    private final static double AVG_WALK_SPEED = 5.3 / 3.6;
    private final static double AVG_CYCLE_SPEED = 14 / 3.6;

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 5) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(1) Network File Path \n" +
                    "(2) Public Transport Network File Path \n" +
                    "(3) Public Transport Schedule File Path \n" +
                    "(4) Output File Path \n" +
                    "(5) Number of Threads \n");
        }

        String networkPath = args[0];
        String ptNetworkPath = args[1];
        String ptSchedulePath = args[2];
        String outputDirectory = args[3];
        int numberOfThreads = Integer.parseInt(args[4]);;

        // Setup config
        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode("bike");
        config.addModule(bicycleConfigGroup);
        PlanCalcScoreConfigGroup planCalcScoreConfigGroup = new PlanCalcScoreConfigGroup();
        log.info("Marginal utility of Money = " + planCalcScoreConfigGroup.getMarginalUtilityOfMoney());
        PlansCalcRouteConfigGroup plansCalcRouteConfigGroup = new PlansCalcRouteConfigGroup();
        plansCalcRouteConfigGroup.setRoutingRandomness(0.);
        log.info("Routing randomness = " + plansCalcRouteConfigGroup.getRoutingRandomness());

        // NON-PT MATRIX CALCULATIONS
        // Set travel time calculation
        TravelTime tt = (link, v, person, vehicle) -> link.getLength() / AVG_CYCLE_SPEED;
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        TravelTime ttCycle = new BicycleTravelTime(linkSpeedCalculator);

        // Set travel disutility calculation (used for routing)
        TravelDisutility td = new DistanceAsTravelDisutility();
        TravelDisutility tdCycle = new BicycleTravelDisutility((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME),
                planCalcScoreConfigGroup, plansCalcRouteConfigGroup, ttCycle, 0);

        // Set additional attributes (to be aggregated)
        TravelAttribute[] travelAttributes = new TravelAttribute[4];
        travelAttributes[0] = link -> (double) link.getAttributes().getAttribute("averageaadt.imp") * link.getLength();
        travelAttributes[1] = link -> ((Integer) link.getAttributes().getAttribute("quietness")).doubleValue() * link.getLength();
        travelAttributes[2] = link -> (double) link.getAttributes().getAttribute("slope") * link.getLength();
        travelAttributes[3] = link -> (double) link.getAttributes().getAttribute("NDVImean") * link.getLength();

        CalculateData calc = new CalculateData(outputDirectory,numberOfThreads, 5);
        calc.loadSamplingPointsFromFile("/Users/corinstaves/Documents/manchester/JIBE/samplingPoints.csv");

        // NETWORK INDICATOR CALCULATIONS
        String[] origins = {"M60 7RA"};
        //calc.calculateRouteIndicators(networkPath,config,tt,td,"testInd_", travelAttributes, TransportMode.bike, l -> true);
        calc.calculateRouteGeometries(networkPath,config,origins,ttCycle,tdCycle,"bikeImp_", TransportMode.bike, l -> true);
        //calc.calculateRouteGeometries(networkPath,config,origins,ttCycle,td,"shortest_", TransportMode.bike, l -> true);


        // PUBLIC TRANSPORT CALCULATIONS (INCOMPLETE)
        // calc.calculatePtIndicators(ptNetworkPath,ptSchedulePath,43200,43560,config,"pt_test2",(l, r) -> true);

        // ACCESSIBILITY CALCULATIONS
        // Set zone weights (should have to do with zone attractiveness, but for now just 1)
//        calc.setZoneWeightsToValue(1.);
//
//        Impedance walkImpedance = c -> Math.exp(-0.001 * c);
//        Impedance bikeImpedance = c -> Math.exp(-0.00007692776 * c);
//
//        calc.calculateAccessibilities(networkPath, config, "accessibilities",
//                tt,td,travelAttributes, walkImpedance, TransportMode.walk,l -> true);
    }

    private static class DistanceAsTravelDisutility implements TravelDisutility {
        public DistanceAsTravelDisutility() {
        }

        public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
            return link.getLength();
        }

        public double getLinkMinimumTravelDisutility(Link link) {
            return link.getLength();
        }
    }



}
