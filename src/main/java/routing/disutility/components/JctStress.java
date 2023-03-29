package routing.disutility.components;

import org.matsim.api.core.v01.network.Link;
import static routing.disutility.components.Crossing.*;


public class JctStress {

    public static double getJunctionStress(Link link, String mode) {

        double stress = 0.;

        if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
            double stressPerM = 0;

            Double crossingAadt = (Double) link.getAttributes().getAttribute("crossAadt") * 0.865;
            double crossingLanes = (double) link.getAttributes().getAttribute("crossLanes");
            double crossingWidth = (double) link.getAttributes().getAttribute("crossWidth");
            double crossingSpeed = (double) link.getAttributes().getAttribute("crossSpeedLimitMPH");
            double crossingSpeed85perc = (double) link.getAttributes().getAttribute("cross85PercSpeed") * 0.621371;
            if(crossingAadt.isNaN()) crossingAadt = 800.;

            Crossing crossingType = Crossing.getType(link,mode);

            if(crossingSpeed85perc >= crossingSpeed*1.1) {
                crossingSpeed = crossingSpeed85perc;
            }

            if(crossingType.equals(UNCONTROLLED)) {
                if(crossingSpeed < 60) {
                    stressPerM = crossingAadt/(300*crossingSpeed + 16500) + crossingSpeed/90 + crossingLanes/3 - 0.5;
                } else {
                    stressPerM = 1.;
                }
            } else if(crossingType.equals(PARALLEL)) {
                if(crossingSpeed <= 30) {
                    stressPerM = crossingAadt/24000 + crossingLanes/3 - 2./3;
                } else {
                    stressPerM = crossingSpeed/90 + 1./3;
                }
            } else if(crossingType.equals(SIGNAL)) {
                if(crossingSpeed < 60) {
                    stressPerM = 0;
                } else {
                    stressPerM = 1.;
                }
            }

            if(stressPerM < 0.) {
                stressPerM = 0;
            } else if (stressPerM > 1.) {
                stressPerM = 1;
            }
            stress = stressPerM * crossingWidth;
        }

        return stress;
    }
}