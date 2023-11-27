package routing.disutility;


import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import resources.Properties;
import resources.Resources;
import routing.Gradient;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkComfort;
import routing.disutility.components.LinkStress;

/**
 * Custom walk and bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeDisutility4 implements TravelDisutility {

    private final static Logger logger = Logger.getLogger(JibeDisutility4.class);
    private final String mode;
    private final double marginalCostOfGradient_s;
    private final double marginalCostOfComfort_s;
    private final double marginalCostAmbience_s;
    private final double marginalCostStress_s;
    private final TravelTime timeCalculator;
    private Network network;
    private Vehicle vehicle;
    private final Boolean dayOverride;
    private final double[] disutilities = new double[Id.getNumberOfIds(Link.class) * 2];

    // Default parameters
    public JibeDisutility4(Network network, Vehicle vehicle, String mode, TravelTime tt, Boolean dayOverride) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not suported for JIBE disutility.");
        }

        this.network = network;
        this.vehicle = vehicle;
        this.mode = mode;
        this.timeCalculator = tt;
        this.dayOverride = dayOverride;
        this.marginalCostOfGradient_s = Resources.instance.getMarginalCost(mode,Properties.GRADIENT);
        this.marginalCostOfComfort_s = Resources.instance.getMarginalCost(mode,Properties.COMFORT);
        this.marginalCostAmbience_s = Resources.instance.getMarginalCost(mode,Properties.AMBIENCE);
        this.marginalCostStress_s = Resources.instance.getMarginalCost(mode,Properties.STRESS);
        printMarginalCosts();
        precalculateDisutility();
    }

    // Custom parameters
    public JibeDisutility4(Network network, Vehicle vehicle, String mode, TravelTime tt, Boolean dayOverride,
                           double marginalCostOfGradient_s, double marginalCostOfComfort_s,
                           double marginalCostAmbience_s, double marginalCostStress_s) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.network = network;
        this.vehicle = vehicle;
        this.mode = mode;
        this.timeCalculator = tt;
        this.dayOverride = dayOverride;
        this.marginalCostOfGradient_s = marginalCostOfGradient_s;
        this.marginalCostOfComfort_s = marginalCostOfComfort_s;
        this.marginalCostAmbience_s = marginalCostAmbience_s;
        this.marginalCostStress_s = marginalCostStress_s;
        printMarginalCosts();
        precalculateDisutility();
    }

    public JibeDisutility4(String mode, TravelTime tt, Boolean dayOverride,
                           double marginalCostOfGradient_s, double marginalCostOfComfort_s,
                           double marginalCostAmbience_s, double marginalCostStress_s) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.network = null;
        this.vehicle = null;
        this.mode = mode;
        this.timeCalculator = tt;
        this.dayOverride = dayOverride;
        this.marginalCostOfGradient_s = marginalCostOfGradient_s;
        this.marginalCostOfComfort_s = marginalCostOfComfort_s;
        this.marginalCostAmbience_s = marginalCostAmbience_s;
        this.marginalCostStress_s = marginalCostStress_s;
        printMarginalCosts();
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    private void printMarginalCosts() {
        logger.info("Initialised JIBE disutility with the following parameters:" +
                "\nMode: " + this.mode +
                "\nMarginal cost of gradient (/s): " + this.marginalCostOfGradient_s +
                "\nMarginal cost of surface comfort (/s): " + this.marginalCostOfComfort_s +
                "\nMarginal cost of ambience (/s): " + this.marginalCostAmbience_s +
                "\nMarginal cost of stress (/s): " + this.marginalCostStress_s);
    }

    public void precalculateDisutility() {
        logger.info("precalculating disutilities...");
        if(dayOverride != null) {
            for(Link link : network.getLinks().values()) {
                double linkDisutility = calculateDisutility(link,dayOverride,vehicle);
                disutilities[link.getId().index() * 2] = linkDisutility;
                disutilities[link.getId().index() * 2 + 1] = linkDisutility;
            }
        } else {
            for(Link link : network.getLinks().values()) {
                disutilities[link.getId().index() * 2] = calculateDisutility(link,true,vehicle);
                disutilities[link.getId().index() * 2 + 1] = calculateDisutility(link,false,vehicle);
            }
        }
    }

    private double calculateDisutility(Link link, boolean day, Vehicle vehicle) {
        if(link.getAllowedModes().contains(this.mode)) {

            double travelTime = timeCalculator.getLinkTravelTime(link, 0., null, vehicle);
            double distance = link.getLength();

            // Gradient factor
            double gradient = Gradient.getGradient(link);
            if(gradient < 0.) gradient = 0.;
            if(gradient > 0.5) gradient = 0.5;

            // Comfort of surface
            double comfortFactor = LinkComfort.getComfortFactor(link);

            // Ambience factor
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
    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
        int idx = link.getId().index() * 2;
        if(time < 21600 || time >= 72000) {
            idx += 1;
        }
        return disutilities[idx];
    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

}
