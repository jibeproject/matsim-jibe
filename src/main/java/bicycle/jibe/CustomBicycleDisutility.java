package bicycle.jibe;


import bicycle.BicycleUtilityUtils;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.BicycleUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class CustomBicycleDisutility implements TravelDisutility {

    private final Map<String, Double> marginalCosts = new HashMap<>();


    private final TravelTime timeCalculator;


    public CustomBicycleDisutility(BicycleConfigGroup bicycleConfigGroup, PlanCalcScoreConfigGroup cnScoringGroup, TravelTime timeCalculator) {
        final PlanCalcScoreConfigGroup.ModeParams bicycleParams = cnScoringGroup.getModes().get(bicycleConfigGroup.getBicycleMode());
        if (bicycleParams == null) {
            throw new NullPointerException("Mode " + bicycleConfigGroup.getBicycleMode() + " is not part of the valid mode parameters " + cnScoringGroup.getModes().keySet());
        }

        this.marginalCosts.put("distance_m",-(bicycleParams.getMonetaryDistanceRate() * cnScoringGroup.getMarginalUtilityOfMoney())
                - bicycleParams.getMarginalUtilityOfDistance());
        this.marginalCosts.put("time_s",-(bicycleParams.getMarginalUtilityOfTraveling() / 3600.0) +
                cnScoringGroup.getPerforming_utils_hr() / 3600.0);
        this.marginalCosts.put("gradient_m_100m",-(bicycleConfigGroup.getMarginalUtilityOfGradient_m_100m()));
        this.marginalCosts.put("comfort_m",-(bicycleConfigGroup.getMarginalUtilityOfComfort_m()));
        this.marginalCosts.put("infrastructure_m",-(bicycleConfigGroup.getMarginalUtilityOfInfrastructure_m()));
        this.marginalCosts.put("roadSpeed_m",2e-4);
        this.marginalCosts.put("ndvi_m",2e-4);

        this.timeCalculator = timeCalculator;

    }

    @Override
    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
        double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);

        Map<String,Double> disutilities = new HashMap<>();

        double distance = link.getLength();

        disutilities.put("time_s", 2 * travelTime);
        disutilities.put("distance_m",distance);

        double comfortFactor = BicycleUtilityUtils.getComfortFactor(link);
        disutilities.put("comfort_m",(1. - comfortFactor) * distance);

        double gradientFactor = BicycleUtilityUtils.getGradientFactor(link);
        disutilities.put("gradient_m_100m",gradientFactor * distance);

        double infrastructureFactor = CustomBicycleUtils.getInfrastructureFactor(link);
        disutilities.put("infrastructure_m", (1. - infrastructureFactor) * distance);

        double roadSpeedFactor = CustomBicycleUtils.getTrafficSpeedFactor(link);
        disutilities.put("roadSpeed_m", (1 - roadSpeedFactor) * distance);

        double ndviFactor = CustomBicycleUtils.getNdviFactor(link);
        disutilities.put("ndvi_m",(1. - ndviFactor) * distance);


        double disutility = 0.;
        for(String factor : disutilities.keySet()) {
            disutility += marginalCosts.get(factor) * disutilities.get(factor);
        }
        return disutility;
    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

    public void printMarginalCosts(Logger log) {
        for(Map.Entry<String,Double> entry : marginalCosts.entrySet()) {
            log.info("Marginal cost for " + entry.getKey() + ": " + entry.getValue());
        }
    }
}