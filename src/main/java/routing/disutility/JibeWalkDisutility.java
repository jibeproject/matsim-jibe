package routing.disutility;


import routing.Gradient;
import routing.disutility.components.LinkAttractiveness;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkStress;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

/**
 * Custom walk and bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeWalkDisutility implements TravelDisutility {

    private final Boolean night;
    private final double marginalCostOfTime_s;
    private final double marginalCostOfDistance_m;
    private final double marginalCostOfGradient_m_100m;
    private final double marinalCostAttractiveness_m;
    private final double marginalCostStress_m;
    private final double marginalCostJunction;

    private final TravelTime timeCalculator;

    // Default parameters
    public JibeWalkDisutility(boolean night, TravelTime timeCalculator) {
        this.night = night;
        this.marginalCostOfTime_s = 2./300;
        this.marginalCostOfDistance_m = 0.;
        this.timeCalculator = timeCalculator;
        this.marginalCostOfGradient_m_100m = 0.01;
        this.marinalCostAttractiveness_m = 6e-3;
        this.marginalCostStress_m = 6e-3;
        this.marginalCostJunction = 6e-2;
    }

    // Custom parameters
    public JibeWalkDisutility(Boolean night, TravelTime timeCalculator,
                              double marginalCostOfTime_s, double marginalCostOfDistance_m,
                              double marginalCostOfGradient_m_100m, double marginalCostAttractiveness_m,
                              double marginalCostStress_m, double marginalCostJunction) {

        this.night = night;
        this.marginalCostOfTime_s = marginalCostOfTime_s;
        this.marginalCostOfDistance_m = marginalCostOfDistance_m;
        this.marginalCostOfGradient_m_100m = marginalCostOfGradient_m_100m;
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
        double disutility = marginalCostOfTime_s * travelTime;

        // Distance disutility (0 by default)
        disutility += marginalCostOfDistance_m * distance;

        // Gradient factor
        double gradient = Gradient.getGradient(link);
        if(gradient < 0.) gradient = 0.;
        disutility += marginalCostOfGradient_m_100m * gradient * distance;

        // Attractiveness factors
        if(marinalCostAttractiveness_m > 0) {
            if(night != null) {
                double attractiveness = LinkAttractiveness.getAttractiveness(link,night);
                disutility += marinalCostAttractiveness_m * attractiveness * distance;
            } else {
                throw new RuntimeException("Positive attractiveness marginal cost specified but day/night switch is null!");
            }
        }


        // Stress factors
        double stress = LinkStress.getWalkStress(link);
        disutility += marginalCostStress_m * stress * distance;

        // Junction stress factor
        double junctionStress = JctStress.getWalkJunctionStress(link);
        disutility += marginalCostJunction * junctionStress;

        return disutility;

    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

    public double getTimeComponent(Link link, double time, Person person, Vehicle vehicle) {
        double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);
        return marginalCostOfTime_s * travelTime;
    }

    public double getDistanceComponent(Link link) {
        return marginalCostOfDistance_m * link.getLength();
    }

    public double getGradientComponent(Link link) {
        double gradient = Gradient.getGradient(link);
        if(gradient < 0.) gradient = 0.;
        return marginalCostOfGradient_m_100m * gradient * link.getLength();
    }

    public double getAttractivenessComponent(Link link) {
        if(night == null) {
            return 0.;
        } else {
            double attractiveness = LinkAttractiveness.getAttractiveness(link, night);
            return marinalCostAttractiveness_m * attractiveness * link.getLength();
        }
    }

    public double getStressComponent(Link link) {
        double stress = LinkStress.getWalkStress(link);
        return marginalCostStress_m * stress * link.getLength();
    }

    public double getJunctionComponent(Link link) {
        double jctStress = JctStress.getWalkJunctionStress(link);
        return marginalCostJunction * jctStress;
    }
}
