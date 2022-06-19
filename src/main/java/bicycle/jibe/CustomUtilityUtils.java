package bicycle.jibe;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.bicycle.BicycleUtils;

import static bicycle.jibe.CycleProtection.*;
import static bicycle.jibe.CycleSafety.*;


public class CustomUtilityUtils {

    public static double getCycleStress(Link link) {

        double stress = 0;

        if((boolean) link.getAttributes().getAttribute("allowsCar")) {
            String junction = (String) link.getAttributes().getAttribute("junction");
            if (junction.equals("roundabout") || junction.equals("circular")) {
                stress = 1.;
            } else {
                double speedLimit = ((Integer) link.getAttributes().getAttribute("speedLimitMPH")).doubleValue();
                double speed85perc = (double) link.getAttributes().getAttribute("veh85percSpeedKPH") * 0.621371;
                Double aadt = (double) link.getAttributes().getAttribute("aadt");
                if(aadt.isNaN()) aadt = 1570.;
                CycleProtection protection = getCycleProtectionType(link);

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

                stress = intercept + speedFactor * speedLimit + aadtFactor * aadt + freightPoiFactor * 0.1;

                if(stress < 0.) {
                    stress = 0;
                } else if (stress > 1.) {
                    stress = 1;
                }
            }
        }
        return stress;
    }

    public static double getWalkStress(Link link) {
        double stress = 0;

        if((boolean) link.getAttributes().getAttribute("allowsCar")) {
            double speedLimit = ((Integer) link.getAttributes().getAttribute("speedLimitMPH")).doubleValue();
            double speed85perc = (double) link.getAttributes().getAttribute("veh85percSpeedKPH") * 0.621371;
            Double aadt = (double) link.getAttributes().getAttribute("aadt");
            if(aadt.isNaN()) aadt = 1570.;

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

    public static double getCycleJunctionStress(Link link) {
        double jctAadtConf = (double) link.getAttributes().getAttribute("jctAadtConf");
        return Math.sqrt(jctAadtConf);
    }

    public static double getWalkJunctionStress(Link link) {
        double jctAadt = (double) link.getAttributes().getAttribute("jctAadt");
        return Math.sqrt(jctAadt);
    }

    public static CycleSafety getDftCategory(Link link) {

        CycleSafety safety = GREEN;

        if((boolean) link.getAttributes().getAttribute("allowsCar")) {
            int speedLimit = (int) link.getAttributes().getAttribute("speedLimitMPH");
            double speed85perc = (double) link.getAttributes().getAttribute("veh85percSpeedKPH") * 0.621371;
            Double aadt = (Double) link.getAttributes().getAttribute("aadt");
            if(aadt.isNaN()) aadt = 3260.;
            CycleProtection protection = getCycleProtectionType(link);

            if((speedLimit <= 20 && speed85perc <= 22) || (speed85perc <= 30 && aadt <= 1000)) {
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
            } else if (speedLimit <= 20 || (speedLimit <= 30 && speed85perc <= 33)) {
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
            } else if (speedLimit <= 30 || (speedLimit <= 40 && speed85perc <= 44)) {
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

    public static CycleProtection getCycleProtectionType(Link link) {

        String cycleosm = (String) link.getAttributes().getAttribute("cycleosm");
        String cycleway = (String) link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY);

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

        double trafficSpeed = (double) link.getAttributes().getAttribute("veh85percSpeedKPH");

        if(trafficSpeed >= 100) {
            return 0.0;
        } else {
            return 1.0 - trafficSpeed/100;
        }
    }

    public static double getQuietnessFactor(Link link) {
        int quietness = (int) link.getAttributes().getAttribute("quietness");
        return quietness / 100.;
    }

    public static double getNdviFactor(Link link) {
        double ndvi = (double) link.getAttributes().getAttribute("ndvi");
        return 1. - ndvi;
    }

    public static double getVgviFactor(Link link) {
        Double vgvi = (double) link.getAttributes().getAttribute("vgvi");
        return 1. - vgvi;
    }

    public static double getLightingFactor(Link link) {
        double lights = (double) link.getAttributes().getAttribute("streetLights");
        return 1. - Math.min(1.,lights / link.getLength());
    }

    public static double getShannonFactor(Link link) {
        double shannon = (double) link.getAttributes().getAttribute("shannon");
        return 1. - Math.min(1.,shannon / 3.);
    }

    public static double getPoiFactor(Link link) {
        double pois = (double) link.getAttributes().getAttribute("POIs");
        return 1 - Math.min(1., pois / link.getLength());
    }

    public static double getNegativePoiFactor(Link link) {
        double negPois = (double) link.getAttributes().getAttribute("negPOIs");
        return Math.min(1.,negPois / link.getLength());
    }

    public static double getFreightPoiFactor(Link link) {
        double hgvPois = (double) link.getAttributes().getAttribute("hgvPOIs");
        return Math.min(1.,hgvPois / link.getLength());
    }

    public static double getCrimeFactor(Link link) {
        double crime = (double) link.getAttributes().getAttribute("crime");
        return Math.min(1.,crime / link.getLength());
    }

    public static double  getGradient(Link link) {
        double gradient = 0.;
        if (link.getFromNode().getCoord().hasZ() && link.getToNode().getCoord().hasZ()) {
            double fromZ = link.getFromNode().getCoord().getZ();
            double toZ = link.getToNode().getCoord().getZ();
            gradient = (toZ - fromZ) / link.getLength();
        }

        return gradient;
    }

    public static double getDayAttractiveness(Link link) {
        double vgvi = getVgviFactor(link);
        double pois = getPoiFactor(link);
        double shannon = getShannonFactor(link);
        double negativePois = getNegativePoiFactor(link);
        double crime = getCrimeFactor(link);

        return vgvi/3 + (pois + shannon)/6 + (negativePois + crime)/6;
    }

    public static double getNightAttractiveness(Link link) {
        double pois = getPoiFactor(link);
        double shannon = getShannonFactor(link);
        double lighting = getLightingFactor(link);
        double negativePois = getNegativePoiFactor(link);
        double crime = getCrimeFactor(link);

        return (pois + shannon)/6 + (lighting + negativePois + crime)*2/9;
    }

    public static double cycleLaneAdjustment(String cycleosm, String cycleway) {
        double factor;

        switch (cycleosm) {
            case "offroad": factor = 1.0; break;
            case "protected": factor = 0.8; break;
            case "painted": factor = 0.6; break;
            case "integrated": factor = 0.0; break;
            case "dismount": factor = 0.0; break;
            default:
                switch(cycleway) {
                    case "track": factor = 1.0; break;
                    case "share_busway": factor = 0.6; break;
                    case "lane": factor = 0.6; break;
                    default: factor = 0.0;
                }
        }
        return factor;
    }

    private static double getInRange(double min, double max, double prop) {
        return min + prop * (max - min);
    }
}
