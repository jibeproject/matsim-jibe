package routing.disutility.components;

import data.Crossing;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import static data.Crossing.*;


public class JctStress {

    public static double getWalkJunctionStress(Link link) {
        double stress = 0;

        if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
            double lanes = (double) link.getAttributes().getAttribute("crossLanes");
            double crossingSpeed = (double) link.getAttributes().getAttribute("crossSpeedLimitMPH");

            Crossing crossingType = Crossing.getType(link, TransportMode.walk);

            if(crossingType.equals(UNCONTROLLED)) {
                if(crossingSpeed < 60) {
                    stress = 8./(3*crossingSpeed + 165) + crossingSpeed/90 + lanes/3 - 0.5;
                } else {
                    stress = 1.;
                }
            } else if(crossingType.equals(PARALLEL)) {
                if(crossingSpeed <= 30) {
                    stress = 1./30 + lanes/3 - 2./3;
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

            if(stress < 0.) {
                stress = 0;
            } else if (stress > 1.) {
                stress = 1;
            }
        }
        return stress;
    }
}