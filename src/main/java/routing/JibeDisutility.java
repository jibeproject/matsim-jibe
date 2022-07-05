package routing;


import routing.utility.LinkAttractiveness;
import routing.utility.JctStress;
import routing.utility.LinkComfort;
import routing.utility.LinkStress;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

/**
 * Custom bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeDisutility implements TravelDisutility {

    private final String mode;
    private final double marginalCostOfTime_s;
    private final double marginalCostOfDistance_m;
    private final double marginalCostOfGradient_m_100m;
    private final double marginalCostOfComfort_m;
    private final double marinalCostAttractiveness_m;
    private final double marginalCostStress_m;
    private final double marginalCostJunction;

    private final TravelTime timeCalculator;

    public JibeDisutility(String mode, TravelTime timeCalculator,
                          double marginalCostOfTime_s, double marginalCostOfDistance_m,
                          double marginalCostOfGradient_m_100m, double marginalCostOfComfort_m,
                          double marginalCostAttractiveness_m, double marginalCostStress_m,
                          double marginalCostJunction) {

        this.mode = mode;
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
        double gradient = Gradient.getGradient(link);
        if(gradient < 0.) gradient = 0.;
        disutility += marginalCostOfGradient_m_100m * gradient * distance;

        // Comfort of surface
        double comfortFactor = LinkComfort.getComfortFactor(link);
        disutility += marginalCostOfComfort_m * (1. - comfortFactor) * distance;

        // Attractiveness factors
        double attractiveness = LinkAttractiveness.getDayAttractiveness(link);
        disutility += marinalCostAttractiveness_m * attractiveness * distance;

        // Stress factors
        double stress = LinkStress.getStress(link,mode);
        disutility += marginalCostStress_m * stress * distance;

        // Junction stress factor
        double junctionStress = JctStress.getJunctionStress(link,mode);
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
        double gradient = Gradient.getGradient(link);
        if(gradient < 0.) gradient = 0.;
        return marginalCostOfGradient_m_100m * gradient * link.getLength();
    }

    public double getSurfaceComponent(Link link) {
        double comfortFactor = LinkComfort.getComfortFactor(link);
        return marginalCostOfComfort_m * (1. - comfortFactor) * link.getLength();
    }

    public double getAttractivenessComponent(Link link) {
        double attractiveness = LinkAttractiveness.getDayAttractiveness(link);
        return marinalCostAttractiveness_m * attractiveness * link.getLength();
    }

    public double getStressComponent(Link link) {
        double stress = LinkStress.getStress(link, mode);
        return marginalCostStress_m * stress * link.getLength();
    }

    public double getJunctionComponent(Link link) {
        double jctStress = JctStress.getJunctionStress(link, mode);
        return marginalCostJunction * jctStress;
    }
}
