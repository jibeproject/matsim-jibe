package routing.utility;

import data.CycleProtection;
import org.matsim.api.core.v01.network.Link;

import static data.CycleProtection.*;


public class LinkStress {

    public static double getStress(Link link, String mode) {
        if(mode.equals("walk")) {
            return getWalkStress(link);
        } else if (mode.equals("bike")) {
            return getCycleStress(link);
        } else {
            throw new RuntimeException("unknown mode " + mode);
        }
    }

    private static double getCycleStress(Link link) {

        double stress = 0;

        if((boolean) link.getAttributes().getAttribute("allowsCar")) {
            String junction = (String) link.getAttributes().getAttribute("junction");
            if (junction.equals("roundabout") || junction.equals("circular")) {
                stress = 1.;
            } else {
                double speedLimit = ((Integer) link.getAttributes().getAttribute("speedLimitMPH")).doubleValue();
                double speed85perc = (double) link.getAttributes().getAttribute("veh85percSpeedKPH") * 0.621371;
                Double aadt = (double) link.getAttributes().getAttribute("aadt") * 0.865;
                if(aadt.isNaN()) aadt = 800.;
                CycleProtection protection = CycleProtection.getType(link);

                if(speed85perc >= speedLimit*1.1) {
                    speedLimit = speed85perc;
                }

                double intercept;
                double speedFactor;
                double aadtFactor;

                if(protection.equals(OFFROAD)) {
                    intercept = 0;
                    speedFactor = 0;
                    aadtFactor = 0;
                } else if(protection.equals(PROTECTED)) {
                    intercept = -1.5;
                    speedFactor = 0.05;
                    aadtFactor = 0;
                } else if (protection.equals(LANE)) {
                    intercept = -1.625;
                    speedFactor = 0.0625;
                    aadtFactor = 0.000125;
                } else {
                    intercept = -1.25;
                    speedFactor = 0.0583;
                    aadtFactor = 0.000167;
                }

                double freightPoiFactor = getFreightPoiFactor(link);

                stress = intercept + speedFactor * speedLimit + aadtFactor * aadt + 0.1 * freightPoiFactor;

                if(stress < 0.) {
                    stress = 0;
                } else if (stress > 1.) {
                    stress = 1;
                }
            }
        }
        return stress;
    }

    private static double getWalkStress(Link link) {
        double stress = 0;

        if((boolean) link.getAttributes().getAttribute("allowsCar")) {
            double speedLimit = ((Integer) link.getAttributes().getAttribute("speedLimitMPH")).doubleValue();
            double speed85perc = (double) link.getAttributes().getAttribute("veh85percSpeedKPH") * 0.621371;
            Double aadt = (double) link.getAttributes().getAttribute("aadt") * 0.865;
            if(aadt.isNaN()) aadt = 800.;

            if(speed85perc >= speedLimit*1.1) {
                speedLimit = speed85perc;
            }

            double freightPoiFactor = getFreightPoiFactor(link);

            stress = -1.625 + 0.0625 * speedLimit + 0.000125 * aadt + 0.2 * freightPoiFactor;

            if(stress < 0.) {
                stress = 0;
            } else if (stress > 1.) {
                stress = 1;
            }
        }

        return stress;
    }

    public static double getFreightPoiFactor (Link link){
        double hgvPois = (double) link.getAttributes().getAttribute("hgvPOIs");
        return Math.min(1., hgvPois / link.getLength());
    }

}