package routing.disutility;


import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import routing.Gradient;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkComfort;
import routing.disutility.components.LinkStress;

import java.util.Objects;

/**
 * Custom walk and bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeDisutility3 implements TravelDisutility {

    private final static double DEFAULT_MARGINAL_COST_WALK_GRADIENT = 4;
    private final static double DEFAULT_MARGINAL_COST_WALK_COMFORT = 0;
    private final static double DEFAULT_MARGINAL_COST_WALK_AMBIENCE = 3;
    private final static double DEFAULT_MARGINAL_COST_WALK_STRESS = 1;

    private final static double DEFAULT_MARGINAL_COST_BIKE_GRADIENT = 15;
    private final static double DEFAULT_MARGINAL_COST_BIKE_COMFORT = 0.15;
    private final static double DEFAULT_MARGINAL_COST_BIKE_AMBIENCE = 1;
    private final static double DEFAULT_MARGINAL_COST_BIKE_STRESS = 3;

    private final static Logger logger = Logger.getLogger(JibeDisutility3.class);
    private final String mode;
    private final double marginalCostOfGradient_s;
    private final double marginalCostOfComfort_s;
    private final double marginalCostAmbience_s;
    private final double marginalCostStress_s;
    private final TravelTime timeCalculator;
    private final Boolean dayNightOverride;

    public JibeDisutility3(String mode, TravelTime tt, Boolean dayNightOverride,
                           double marginalCostOfGradient_s, double marginalCostOfComfort_s,
                           double marginalCostAmbience_s, double marginalCostStress_s) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.mode = mode;
        this.timeCalculator = tt;
        this.dayNightOverride = dayNightOverride;
        this.marginalCostOfGradient_s = marginalCostOfGradient_s;
        this.marginalCostOfComfort_s = marginalCostOfComfort_s;
        this.marginalCostAmbience_s = marginalCostAmbience_s;
        this.marginalCostStress_s = marginalCostStress_s;
        printMarginalCosts();
    }

    // Default parameters
    public JibeDisutility3(String mode, TravelTime tt, Boolean dayNightOverride) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }
        this.mode = mode;
        this.timeCalculator = tt;
        this.dayNightOverride = dayNightOverride;

        boolean walk = mode.equals(TransportMode.walk);
        this.marginalCostOfGradient_s = walk ? DEFAULT_MARGINAL_COST_WALK_GRADIENT : DEFAULT_MARGINAL_COST_BIKE_GRADIENT;
        this.marginalCostOfComfort_s = walk ? DEFAULT_MARGINAL_COST_WALK_COMFORT : DEFAULT_MARGINAL_COST_BIKE_COMFORT;
        this.marginalCostAmbience_s = walk ? DEFAULT_MARGINAL_COST_WALK_AMBIENCE : DEFAULT_MARGINAL_COST_BIKE_AMBIENCE;
        this.marginalCostStress_s = walk ? DEFAULT_MARGINAL_COST_WALK_STRESS : DEFAULT_MARGINAL_COST_BIKE_STRESS;
        printMarginalCosts();
    }

    // Custom parameters ambience and stress only
    public JibeDisutility3(String mode, TravelTime tt, Boolean dayNightOverride,
                           double marginalCostAmbience_s, double marginalCostStress_s) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.mode = mode;
        this.timeCalculator = tt;
        this.dayNightOverride = dayNightOverride;

        boolean walk = mode.equals(TransportMode.walk);
        this.marginalCostOfGradient_s = walk ? DEFAULT_MARGINAL_COST_WALK_GRADIENT : DEFAULT_MARGINAL_COST_BIKE_GRADIENT;
        this.marginalCostOfComfort_s = walk ? DEFAULT_MARGINAL_COST_WALK_COMFORT : DEFAULT_MARGINAL_COST_BIKE_COMFORT;
        this.marginalCostAmbience_s = marginalCostAmbience_s;
        this.marginalCostStress_s = marginalCostStress_s;
        printMarginalCosts();
    }

    private void printMarginalCosts() {
        logger.info("Initialised JIBE disutility with the following parameters:" +
                "\nMode: " + this.mode +
                "\nMarginal cost of gradient (/s): " + this.marginalCostOfGradient_s +
                "\nMarginal cost of surface comfort (/s): " + this.marginalCostOfComfort_s +
                "\nMarginal cost of ambience (/s): " + this.marginalCostAmbience_s +
                "\nMarginal cost of stress (/s): " + this.marginalCostStress_s);
    }

    @Override
    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {

        if(link.getAllowedModes().contains(this.mode)) {

            double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);
            double distance = link.getLength();

            // Gradient factor
            double gradient = Gradient.getGradient(link);
            if(gradient < 0.) gradient = 0.;
            if(gradient > 0.5) gradient = 0.5;

            // Comfort of surface
            double comfortFactor = LinkComfort.getComfortFactor(link);

            // Set day/night
            boolean day = Objects.requireNonNullElseGet(dayNightOverride, () -> (time >= 21600 && time < 72000));

            // Ambience
            double ambience = day ? LinkAmbience.getDayAmbience(link) : LinkAmbience.getNightAmbience(link);

            // Stress factors
            double linkStress = LinkStress.getStress(link,mode);

            // Link disutility
            double disutility = travelTime * (1 +
                    marginalCostOfGradient_s * gradient +
                    marginalCostOfComfort_s * comfortFactor +
                    marginalCostAmbience_s * ambience +
                    marginalCostStress_s * linkStress);

            // Junction stress factor
            if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                double junctionStress = JctStress.getStress(link,mode);
                double junctionWidth = (double) link.getAttributes().getAttribute("crossWidth");
                if(junctionWidth > distance) junctionWidth = distance;
                double junctionTime = travelTime * (junctionWidth / distance);

                disutility += marginalCostStress_s * junctionTime * junctionStress;
            }


            if(Double.isNaN(disutility)) {
                throw new RuntimeException("Null JIBE disutility for link " + link.getId().toString());
            }

            return disutility;

        } else {
            return Double.NaN;
        }
    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

    public double getJunctionComponent(Link link, Vehicle vehicle) {
        if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
            double distance = link.getLength();
            double travelTime = timeCalculator.getLinkTravelTime(link, 0., null, vehicle);
            double junctionStress = JctStress.getStress(link,mode);
            double junctionWidth = (double) link.getAttributes().getAttribute("crossWidth");
            if(junctionWidth > distance) junctionWidth = distance;
            double junctionTime = travelTime * (junctionWidth / distance);
            return marginalCostStress_s * junctionTime * junctionStress;
        }
        else return 0;
    }
}
