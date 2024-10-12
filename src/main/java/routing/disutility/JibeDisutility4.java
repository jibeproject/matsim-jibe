package routing.disutility;


import estimation.RouteAttribute;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import java.util.List;

/**
 * Custom walk and bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeDisutility4 implements TravelDisutility {

    private final static Logger logger = Logger.getLogger(JibeDisutility4.class);
    private final String mode;
    private final TravelTime timeCalculator;
    private final Network network;
    private final Vehicle vehicle;

    private final List<RouteAttribute> attributes;
    private final double[] weights;
    private final double[] disutilities = new double[Id.getNumberOfIds(Link.class)];

    // Custom parameters
    public JibeDisutility4(Network network, Vehicle vehicle, String mode, TravelTime tt,
                           List<RouteAttribute> attributes, double[] weights) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.network = network;
        this.vehicle = vehicle;
        this.mode = mode;
        this.timeCalculator = tt;
        this.attributes = attributes;
        this.weights = weights;
        check();
        precalculateDisutility();
        printMarginalWeights();
    }

    private void precalculateDisutility() {
        for(Link link : network.getLinks().values()) {
            disutilities[link.getId().index()] = calculateDisutility(link);
        }
//        logger.info("precalculated disutilities.");
    }

    private void printMarginalWeights() {
        StringBuilder logStatement = new StringBuilder();
        logStatement.append("Initialised ").append(mode.toUpperCase()).append(" disutility with weights: ");
        for(int i = 0; i < attributes.size() ; i++) {
            logStatement.append(attributes.get(i).getName()).append("=").append(String.format("%.5f",weights[i])).append(", ");
        }
        logStatement.delete(logStatement.length()-2,logStatement.length());
        logger.info(logStatement.toString());
    }

    private void check() {
        if(weights.length != attributes.size()) {
            throw new RuntimeException("Size of marginal weights array (" + weights.length + ") does not match size of attributes array (" + attributes.size() + ")");
        }
        for(int i = 0 ; i < weights.length ; i++) {
            if(weights[i] < 0) {
                throw new RuntimeException("Weight for parameter \"" + attributes.get(i).getName() + "\" < 0! Cannot compute disutilities!");
            }
        }
    }

    private double calculateDisutility(Link link) {
        if(link.getAllowedModes().contains(this.mode)) {

            double linkTime = timeCalculator.getLinkTravelTime(link, 0., null, vehicle);

            // compute expansion
            double streetEnvironmentAdjustment = 1.;
            for(int i = 0; i < attributes.size() ; i++) {
                streetEnvironmentAdjustment += weights[i] * attributes.get(i).getValue(link);
            }

            // Link disutility
            double disutility = linkTime * streetEnvironmentAdjustment;

            if(Double.isNaN(disutility)) {
                throw new RuntimeException("Null disutility for link " + link.getId().toString());
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
