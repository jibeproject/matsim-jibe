package routing.disutility.components;

import org.matsim.api.core.v01.network.Link;


public class LinkStress {

    public static double getWalkStress(Link link) {

        if(!link.getAllowedModes().contains("walk")) {
            return Double.NaN;
        } else {

            double stress = 0;

            if ((boolean) link.getAttributes().getAttribute("allowsCar")) {
                double speedLimit = ((Integer) link.getAttributes().getAttribute("speedLimitMPH")).doubleValue();

                stress = speedLimit / 50;

                if (stress < 0.) {
                    stress = 0;
                } else if (stress > 1.) {
                    stress = 1;
                }
            }
            return stress;
        }
    }

}