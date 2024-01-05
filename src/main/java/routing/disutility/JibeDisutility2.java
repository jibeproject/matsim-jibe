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
    private final double mcTime_s;
    private final double mcDistance_m;
    private final double mcGradient_m_100m;
    private final double mcVgviLight;
    private final double mcStressLink;
    private final double mcStressJct;
    private final TravelTime timeCalculator;
    private final Network network;
    private final Person person;
    private final Vehicle vehicle;

    private final double[] disutilities = new double[Id.getNumberOfIds(Link.class) * 2];

    // Custom parameters
    public JibeDisutility2(Network network, Person person, Vehicle vehicle, String mode, TravelTime tt,
                           double mcTime_s, double mcDistance_m,
                           double mcGradient_m_100m, double mcVgviLight,
                           double mcStressLink, double mcStressJct) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.network = network;
        this.person = person;
        this.vehicle = vehicle;
        this.mode = mode;
        this.timeCalculator = tt;
        this.mcTime_s = mcTime_s;
        this.mcDistance_m = mcDistance_m;
        this.mcGradient_m_100m = mcGradient_m_100m;
        this.mcVgviLight = mcVgviLight;
        this.mcStressLink = mcStressLink;
        this.mcStressJct = mcStressJct;
        printMarginalCosts();
        precalculateDisutility();
    }

    private void printMarginalCosts() {
        logger.info("Initialised JIBE disutility with the following parameters:" +
                "\nMode: " + this.mode +
                "\nMarginal cost of time (/s): " + this.mcTime_s +
                "\nMarginal cost of distance (/m): " + this.mcDistance_m +
                "\nMarginal cost of gradient (m/100m): " + this.mcGradient_m_100m +
                "\nMarginal cost of lighting/vgvi (/m): " + this.mcVgviLight +
                "\nMarginal cost of LINK stress (/m): " + this.mcStressLink +
                "\nMarginal cost of JCT stress (/m): " + this.mcStressJct);
    }

    private void precalculateDisutility() {
        logger.info("precalculating disutilities...");
        for(Link link : network.getLinks().values()) {
            disutilities[link.getId().index() * 2] = calculateDisutility(link,true,person,vehicle);
            disutilities[link.getId().index() * 2 + 1] = calculateDisutility(link,false,person,vehicle);
        }
    }

    private double calculateDisutility(Link link, boolean day, Person person, Vehicle vehicle) {

        if(link.getAllowedModes().contains(this.mode)) {

            double travelTime = timeCalculator.getLinkTravelTime(link, 0., person, vehicle);

            double distance = link.getLength();

            // Travel time disutility
            double disutility = mcTime_s * travelTime;

            // Distance disutility (0 by default)
            disutility += mcDistance_m * distance;

            // Gradient factor
            double gradient = Gradient.getGradient(link);
            if(gradient < 0.) gradient = 0.;
            disutility += mcGradient_m_100m * gradient * distance;

            // Lighting / VGVI
            double vgviLight;
            if(day) {
                // Daytime - use VGVI
                vgviLight = LinkAmbience.getVgviFactor(link);
            } else {
                // Nighttime - use Street Lighting
                vgviLight = LinkAmbience.getDarknessFactor(link);
            }
            disutility += mcVgviLight * vgviLight * distance;

            // Link Stress
            double linkStress = LinkStress.getStress(link,mode);
            disutility += mcStressLink * linkStress * distance;

            // Junction stress factor
            double junctionStress = JctStress.getStress(link,mode);
            double crossingWidth = (double) link.getAttributes().getAttribute("crossWidth");
            disutility += mcStressJct * junctionStress * crossingWidth;

            // Check it's not NAN
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
