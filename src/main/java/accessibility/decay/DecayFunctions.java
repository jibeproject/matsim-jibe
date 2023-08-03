package accessibility.decay;

import accessibility.RunIntervention;
import accessibility.resources.AccessibilityProperties;
import accessibility.resources.AccessibilityResources;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import trads.PercentileCalculator;
import trip.Purpose;

import java.io.IOException;

public class DecayFunctions {

    public static final Logger log = Logger.getLogger(DecayFunctions.class);

    public static final DecayFunction WALK_DIST = new Exponential(0.001203989);
    public static final DecayFunction WALK_DIST2 =  new Exponential(0.001086178);

    public static final DecayFunction WALK_JIBE = new Exponential(0.1147573);
    public static final DecayFunction WALK_JIBE2 =new Exponential(0.0974147);

    public static final DecayFunction BIKE_JIBE = new Exponential(0.04950999);
    public static final DecayFunction BIKE_JIBE2 = new Exponential(0.0800476);

    public static final DecayFunction BIKE_DIST = new Exponential(0.0003104329);

    public static final DecayFunction BIKE_DIST_FOOD = new Exponential(0.0005630586);
    public static final DecayFunction WALK_DIST_FOOD = new Exponential(0.001203989);

    public static final DecayFunction WALK_CUGA1 = new CumulativeGaussian(200,57708);

    public static DecayFunction getFromProperties(Network network, Geometry networkBoundary) throws IOException {

        // Decay function
        String decayType = AccessibilityResources.instance.getString(AccessibilityProperties.DECAY_FUNCTION);
        double cutoffTime = AccessibilityResources.instance.getDouble(AccessibilityProperties.CUTOFF_TIME);
        double cutoffDist = AccessibilityResources.instance.getDouble(AccessibilityProperties.CUTOFF_DISTANCE);

        if(decayType == null) {
            log.warn("No decay function type specified.");
            return null;
        } else if (decayType.equalsIgnoreCase("exponential")) {
            double beta = AccessibilityResources.instance.getDouble(AccessibilityProperties.BETA);
            // Estimate from trads if beta not given
            if(Double.isNaN(beta))  {
                beta = estimateExpBetaFromTRADS(network,networkBoundary);
            }
            log.info("Initialising exponential decay function with the following parameters:" +
                    "\nBeta: " + beta +
                    "\nTime cutoff (seconds): " + cutoffTime +
                    "\nDistance cutoff (meters): " + cutoffDist);
            return new Exponential(beta,cutoffTime,cutoffDist);
        } else if (decayType.equalsIgnoreCase("power")) {
            double a = AccessibilityResources.instance.getDouble(AccessibilityProperties.A);
            log.info("Initialising power decay function with the following parameters:" +
                    "\na: " + a +
                    "\nTime cutoff (seconds): " + cutoffTime +
                    "\nDistance cutoff (meters): " + cutoffDist);
            return new Power(a,cutoffTime,cutoffDist);
        } else if (decayType.equalsIgnoreCase("cumulative")) {
            log.info("Initialising cumulative decay function with the following parameters:" +
                    "\nTime cutoff (seconds): " + cutoffTime +
                    "\nDistance cutoff (meters): " + cutoffDist);
            return new Cumulative(cutoffTime, cutoffDist);
        } else if (decayType.equalsIgnoreCase("gaussian")) {
            double v = AccessibilityResources.instance.getDouble(AccessibilityProperties.V);
            log.info("Initialising gaussian decay function with the following parameters:" +
                    "\nv: " + v +
                    "\nTime cutoff (seconds): " + cutoffTime +
                    "\nDistance cutoff (meters): " + cutoffDist);
            return new Gaussian(v,cutoffTime,cutoffDist);
        } else if (decayType.equalsIgnoreCase("cumulative gaussian")) {
            double a = AccessibilityResources.instance.getDouble(AccessibilityProperties.A);
            double v = AccessibilityResources.instance.getDouble(AccessibilityProperties.V);
            log.info("Initialising cumulative gaussian decay function with the following parameters:" +
                    "\na: " + a +
                    "\nv: " + v +
                    "\nTime cutoff (seconds): " + cutoffTime +
                    "\nDistance cutoff (meters): " + cutoffDist);
            return new CumulativeGaussian(a,v,cutoffTime,cutoffDist);
        } else {
            log.warn("Do not recognise decay function type \"" + decayType + "\"");
            return null;
        }
    }

    public static double estimateExpBetaFromTRADS(Network network, Geometry networkBoundary) throws IOException {

        String mode = AccessibilityResources.instance.getMode();
        TravelTime tt = AccessibilityResources.instance.getTravelTime();
        Vehicle veh = AccessibilityResources.instance.getVehicle();
        TravelDisutility td = AccessibilityResources.instance.getTravelDisutility();
        Purpose.PairList includedPurposePairs = AccessibilityResources.instance.getPurposePairs();

        log.info("Estimating exponential decay function using TRADS survey");
        String outputCsv = AccessibilityResources.instance.getString(AccessibilityProperties.TRADS_OUTPUT_CSV);
        return PercentileCalculator.estimateBeta(mode,veh,tt,td,includedPurposePairs,
                network,network,networkBoundary,outputCsv);
    }

}
