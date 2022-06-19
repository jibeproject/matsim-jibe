package bicycle.jibe;


import bicycle.BicycleUtilityUtils;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

/**
 * Custom bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeWalkRoute implements TravelDisutility {

    private final double marginalCostOfTime_s;
    private final double marginalCostOfDistance_m;
    private final double marginalCostOfGradient_m_100m;
    private final double marginalCostOfComfort_m;
    private final double marinalCostAttractiveness_m;
    private final double marginalCostStress_m;
    private final double marginalCostJunction;

    private final TravelTime timeCalculator;

    public JibeWalkRoute(TravelTime timeCalculator,
                         double marginalCostOfTime_s, double marginalCostOfDistance_m,
                         double marginalCostOfGradient_m_100m, double marginalCostOfComfort_m,
                         double marginalCostAttractiveness_m, double marginalCostStress_m,
                         double marginalCostJunction) {

        this.marginalCostOfTime_s = marginalCostOfTime_s;
        this.marginalCostOfDistance_m = marginalCostOfDistance_m;
        this.marginalCostOfGradient_m_100m = marginalCostOfGradient_m_100m;
        this.marginalCostOfComfort_m = marginalCostOfComfort_m;
        this.marinalCostAttractiveness_m = marginalCostAttractiveness_m;
        this.marginalCostStress_m = marginalCostStress_m;
        this.marginalCostJunction = marginalCostJunction;

        this.timeCalculator = timeCalculator;

    }

    @Override
    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
        double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);

        double distance = link.getLength();

        // Travel time disutility
        double disutility = marginalCostOfTime_s * 2 * travelTime;

        // Distance disutility (0 by default)
        disutility += marginalCostOfDistance_m * distance;

        // Gradient factor
        double gradientFactor = BicycleUtilityUtils.getGradientFactor(link);
        disutility += marginalCostOfGradient_m_100m * gradientFactor * distance;

        // Attractiveness factors
        double attractiveness = CustomUtilityUtils.getDayAttractiveness(link);
        disutility += marinalCostAttractiveness_m * attractiveness * distance;

        // Stress factors
        double stress = CustomUtilityUtils.getWalkStress(link);
        disutility += marginalCostStress_m * stress * distance;

        // Junction stress factor
        double junctionStress = CustomUtilityUtils.getWalkJunctionStress(link);
        disutility += marginalCostJunction * junctionStress;

        return disutility;

    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

    public double getTimeComponent(Link link, double time, Person person, Vehicle vehicle) {
        double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);
        return marginalCostOfTime_s * 2 * travelTime;
    }

    public double getDistanceComponent(Link link) {
        return marginalCostOfDistance_m * link.getLength();
    }

    public double getGradientComponent(Link link) {
        double gradientFactor = BicycleUtilityUtils.getGradientFactor(link);
        return marginalCostOfGradient_m_100m * gradientFactor * link.getLength();
    }

    public double getSurfaceComponent(Link link) {
        double comfortFactor = BicycleUtilityUtils.getComfortFactor(link);
        return marginalCostOfComfort_m * (1. - comfortFactor) * link.getLength();
    }

    public double getAttractivenessComponent(Link link) {
        double attractiveness = CustomUtilityUtils.getDayAttractiveness(link);
        return marinalCostAttractiveness_m * attractiveness * link.getLength();
    }

    public double getStressComponent(Link link) {
        double stress = CustomUtilityUtils.getCycleStress(link);
        return marginalCostStress_m * stress * link.getLength();
    }

    public double getJunctionComponent(Link link) {
        double jctStress = CustomUtilityUtils.getCycleJunctionStress(link);
        return marginalCostJunction * jctStress;
    }
}
