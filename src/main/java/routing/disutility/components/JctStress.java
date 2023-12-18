package routing.disutility.components;

import org.matsim.api.core.v01.network.Link;
import static routing.disutility.components.Crossing.*;


public class JctStress {

    public static double getStress(Link link, String mode) {

        if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
            double stress = 0;

            Double crossingAadt = (Double) link.getAttributes().getAttribute("crossAadt") * 0.865;
            double crossingLanes = (double) link.getAttributes().getAttribute("crossLanes");
            double crossingSpeed = (double) link.getAttributes().getAttribute("crossSpeedLimitMPH");
            double crossingSpeed85perc = (double) link.getAttributes().getAttribute("cross85PercSpeed") * 0.621371;
            if(crossingAadt.isNaN()) crossingAadt = 800.;

            Crossing crossingType = Crossing.getType(link,mode);

            if(crossingSpeed85perc >= crossingSpeed*1.1) {
                crossingSpeed = crossingSpeed85perc;
            }

            if(crossingType.equals(UNCONTROLLED)) {
                if(crossingSpeed < 60) {
                    stress = crossingAadt/(300*crossingSpeed + 16500) + crossingSpeed/90 + crossingLanes/3 - 0.5;
                } else {
                    stress = 1.;
                }
            } else if(crossingType.equals(PARALLEL)) {
                if(crossingSpeed <= 30) {
                    stress = crossingAadt/24000 + crossingLanes/3 - 2./3;
                } else {
                    stress = crossingSpeed/90 + 1./3;
                }
            } else if(crossingType.equals(SIGNAL)) {
                if(crossingSpeed < 60) {
                    stress = 0;
                } else {
                    stress = 1.;
                }
            }

            // Ensure between 0 and 1
            if(stress < 0.) {
                stress = 0;
            } else if (stress > 1.) {
                stress = 1;
            }

            // Compare to link stress (and take the highest)
            double linkStress = LinkStress.getStress(link,mode);
            if(stress < linkStress) {
                return linkStress;
            }

            return stress;
        }
        return 0;
    }
}