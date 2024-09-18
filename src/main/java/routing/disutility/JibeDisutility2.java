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
import routing.Gradient;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;

/**
 * Custom walk and bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeDisutility2 implements TravelDisutility {

    private final static Logger logger = Logger.getLogger(JibeDisutility2.class);
    private final String mode;
    private final double marginalCostGradient;
    private final double marginalCostVgvi;
    private final double marginalCostLinkStress;
    private final double marginalCostJctStress;
    private final TravelTime timeCalculator;
    private final Network network;
    private final Vehicle vehicle;
    private final double[] disutilities = new double[Id.getNumberOfIds(Link.class)];

    // Custom parameters
    public JibeDisutility2(Network network, Vehicle vehicle, String mode, TravelTime tt,
                           double marginalCostGradient, double marginalCostVgvi,
                           double marginalCostLinkStress, double marginalCostJctStress) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.network = network;
        this.vehicle = vehicle;
        this.mode = mode;
        this.timeCalculator = tt;
        this.marginalCostGradient = marginalCostGradient;
        this.marginalCostVgvi = marginalCostVgvi;
        this.marginalCostLinkStress = marginalCostLinkStress;
        this.marginalCostJctStress = marginalCostJctStress;
        printMarginalCosts();
        precalculateDisutility();
    }

    private void printMarginalCosts() {
        logger.info("Initialised JIBE disutility with the following parameters:" +
                "\nMode: " + this.mode +
                "\nMarginal cost of gradient (/s): " + this.marginalCostGradient +
                "\nMarginal cost of vgvi (/s): " + this.marginalCostVgvi +
                "\nMarginal cost of link stress (/s): " + this.marginalCostLinkStress +
                "\nMarginal cost of jct stress (/s): " + this.marginalCostJctStress);
    }

    public void precalculateDisutility() {
        for(Link link : network.getLinks().values()) {
            disutilities[link.getId().index()] = calculateDisutility(link);
        }
        logger.info("precalculated disutilities.");
    }

    private double calculateDisutility(Link link) {
        if(link.getAllowedModes().contains(this.mode)) {

            double linkTime = timeCalculator.getLinkTravelTime(link, 0., null, vehicle);
            double linkLength = link.getLength();

            // Gradient factor
            double gradient = Math.max(Math.min(Gradient.getGradient(link),0.5),0.);

            // VGVI
            double vgvi = Math.max(0.,0.81 - LinkAmbience.getVgviFactor(link));

            // Link stress
            double linkStress = LinkStress.getStress(link,mode);

            // Junction stress
            double jctStress = 0;
            if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                double junctionWidth = Math.min(linkLength,(double) link.getAttributes().getAttribute("crossWidth"));
                jctStress = (junctionWidth / linkLength) * JctStress.getStress(link,mode);
            }

            // Link disutility
            double disutility = linkTime * (1 +
                    marginalCostGradient * gradient +
                    marginalCostVgvi * vgvi +
                    marginalCostLinkStress * linkStress +
                    marginalCostJctStress * jctStress);

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
        return disutilities[link.getId().index()];
    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

}
