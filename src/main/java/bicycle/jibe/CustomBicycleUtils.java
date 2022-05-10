package bicycle.jibe;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.bicycle.BicycleUtils;

import static bicycle.jibe.CycleProtection.*;
import static bicycle.jibe.CycleSafety.*;


public class CustomBicycleUtils {

    public static CycleSafety getLinkSafety(Link link) {

        CycleSafety safety = GREEN;

        if((boolean) link.getAttributes().getAttribute("allowsCar")) {
            int speedLimit = (int) link.getAttributes().getAttribute("speedLimitMPH");
            double aadt = (double) link.getAttributes().getAttribute("aadt");
            String cycleosm = (String) link.getAttributes().getAttribute("cycleosm");
            String cyclewaytype = (String) link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY);

            CycleProtection protection = getCycleProtectionType(cycleosm,cyclewaytype);

            if(speedLimit <= 20) {
                if(protection.equals(LANE)) {
                    if (aadt >= 4000) {
                        safety = AMBER;
                    }
                } else if (protection.equals(MIXED)) {
                    if (aadt >= 4000) {
                        safety = RED;
                    } else if (aadt >= 2000) {
                        safety = AMBER;
                    }
                }
            } else if (speedLimit <= 30) {
                if(protection.equals(LANE)) {
                    if (aadt >= 4000) {
                        safety = RED;
                    } else {
                        safety = AMBER;
                    }
                } else if (protection.equals(MIXED)) {
                    if (aadt >= 2000) {
                        safety = RED;
                    } else {
                        safety = AMBER;
                    }
                }
            } else if (speedLimit <= 40) {
                if (protection.equals(LANE) || protection.equals(MIXED)) {
                    safety = RED;
                } else if (protection.equals(PROTECTED)) {
                    safety = AMBER;
                }
            } else {
                if (!protection.equals(OFFROAD)) {
                    safety = RED;
                }
            }
        }
        return safety;
    }

    public static CycleProtection getCycleProtectionType(String cycleosm, String cycleway) {
        switch (cycleosm) {
            case "offroad":
                return OFFROAD;
            case "protected":
                return PROTECTED;
            case "painted":
                return LANE;
            case "integrated":
                return MIXED;
            default:
                switch(cycleway) {
                    case "track":
                        return PROTECTED;
                    case "share_busway":
                    case "lane":
                        return LANE;
                    default:
                        return MIXED;
                }
        }
    }

    public static double getInfrastructureFactor(Link link) {

        String type = (String) link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE);
        String cycleosm = (String) link.getAttributes().getAttribute("cycleosm");
        String cyclewaytype = (String) link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY);

        double infrastructureFactor = 1.0;
        if (type != null) {
            if (type.equals("trunk")) {
                infrastructureFactor = getInRange(0.05,0.95, cycleLaneAdjustment(cycleosm,cyclewaytype));
            } else if (type.equals("primary") || type.equals("primary_link")) {
                infrastructureFactor = getInRange(0.10,0.95, cycleLaneAdjustment(cycleosm,cyclewaytype));
            } else if (type.equals("secondary") || type.equals("secondary_link")) {
                infrastructureFactor = getInRange(0.30,0.95, cycleLaneAdjustment(cycleosm,cyclewaytype));
            } else if (type.equals("tertiary") || type.equals("tertiary_link")) {
                infrastructureFactor = getInRange(0.40,0.95, cycleLaneAdjustment(cycleosm,cyclewaytype));
            } else if (type.equals("unclassified")) {
                infrastructureFactor = getInRange(0.90,0.95, cycleLaneAdjustment(cycleosm,cyclewaytype));
            } else if (type.equals("service") || type.equals("living_street") || type.equals("minor")) {
                infrastructureFactor = .95;
            } else if (type.equals("cycleway") || type.equals("path")) {
                infrastructureFactor = 1.00;
            } else if (type.equals("footway") || type.equals("track") || type.equals("pedestrian")) {
                infrastructureFactor = .95;
            } else if (type.equals("steps")) {
                infrastructureFactor = .10;
            }
        } else {
            infrastructureFactor = .85;
        }
        return infrastructureFactor;
    }

    public static double getTrafficSpeedFactor(Link link) {

        double trafficSpeed = (double) link.getAttributes().getAttribute("trafficSpeedKPH");

        if(trafficSpeed >= 100) {
            return 0.0;
        } else {
            return 1.0 - trafficSpeed/100;
        }
    }

    public static double getNdviFactor(Link link) {
        double ndvi = (double) link.getAttributes().getAttribute("ndvi");
        return ndvi;
    }

    public static double cycleLaneAdjustment(String cycleosm, String cycleway) {
        double factor;

        switch (cycleosm) {
            case "offroad": factor = 1.0; break;
            case "protected": factor = 0.8; break;
            case "painted": factor = 0.6; break;
            case "integrated": factor = 0.4; break;
            case "dismount": factor = 0.0; break;
            default:
                switch(cycleway) {
                    case "track": factor = 1.0; break;
                    case "share_busway": factor = 0.8; break;
                    case "lane": factor = 0.4; break;
                    default: factor = 0.0;
                }
        }
        return factor;
    }

    private static double getInRange(double min, double max, double prop) {
        return min + prop * (max - min);
    }
}
