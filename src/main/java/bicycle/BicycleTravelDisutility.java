package bicycle;


import org.apache.log4j.Logger;
import org.matsim.contrib.bicycle.*;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import java.util.Random;

/**
 * @author smetzler, dziemke
 * based on RandomizingTimeDistanceTravelDisutility and adding more components
 */
public class BicycleTravelDisutility implements TravelDisutility {

    private final double marginalCostOfTime_s;
    private final double marginalCostOfDistance_m;
    private final double marginalCostOfInfrastructure_m;
    private final double marginalCostOfComfort_m;
    private final double marginalCostOfGradient_m_100m;

    private final TravelTime timeCalculator;


    public BicycleTravelDisutility(BicycleConfigGroup bicycleConfigGroup, PlanCalcScoreConfigGroup cnScoringGroup,TravelTime timeCalculator) {
        final PlanCalcScoreConfigGroup.ModeParams bicycleParams = cnScoringGroup.getModes().get(bicycleConfigGroup.getBicycleMode());
        if (bicycleParams == null) {
            throw new NullPointerException("Mode " + bicycleConfigGroup.getBicycleMode() + " is not part of the valid mode parameters " + cnScoringGroup.getModes().keySet());
        }

        this.marginalCostOfDistance_m = -(bicycleParams.getMonetaryDistanceRate() * cnScoringGroup.getMarginalUtilityOfMoney())
                - bicycleParams.getMarginalUtilityOfDistance();
        this.marginalCostOfTime_s = -(bicycleParams.getMarginalUtilityOfTraveling() / 3600.0) + cnScoringGroup.getPerforming_utils_hr() / 3600.0;

        this.marginalCostOfInfrastructure_m = -(bicycleConfigGroup.getMarginalUtilityOfInfrastructure_m());
        this.marginalCostOfComfort_m = -(bicycleConfigGroup.getMarginalUtilityOfComfort_m());
        this.marginalCostOfGradient_m_100m = -(bicycleConfigGroup.getMarginalUtilityOfGradient_m_100m());

        this.timeCalculator = timeCalculator;
    }

    @Override
    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
        double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);

        double distance = link.getLength();

        double travelTimeDisutility = marginalCostOfTime_s * travelTime;
        double distanceDisutility = marginalCostOfDistance_m * distance;

        double comfortFactor = BicycleUtilityUtils.getComfortFactor(link);
        double comfortDisutility = marginalCostOfComfort_m * (1. - comfortFactor) * distance;

        double infrastructureFactor = BicycleUtilityUtils.getInfrastructureFactor(link);
        double infrastructureDisutility = marginalCostOfInfrastructure_m * (1. - infrastructureFactor) * distance;

        double gradientFactor = BicycleUtilityUtils.getGradientFactor(link);
        double gradientDisutility = marginalCostOfGradient_m_100m * gradientFactor * distance;

        // TODO Gender
        // TODO Activity
        // TODO Other influence factors

        double disutility = 2 * travelTimeDisutility + distanceDisutility + infrastructureDisutility + comfortDisutility + gradientDisutility;
        return disutility;
    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

    public void printDefaultParams(Logger log) {
        log.info("Marginal cost of distance (m) = " + this.marginalCostOfDistance_m);
        log.info("Marginal cost of time (s) = " + this.marginalCostOfTime_s);
        log.info("Marginal cost of infrastructure (m) = " + this.marginalCostOfInfrastructure_m);
        log.info("Marginal cost of comfort (m) = " + this.marginalCostOfComfort_m);
        log.info("Marginal cost of gradient (m/100m) = " + this.marginalCostOfGradient_m_100m);
    }
}