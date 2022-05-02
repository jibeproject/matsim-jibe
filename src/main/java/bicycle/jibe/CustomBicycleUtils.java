package bicycle.jibe;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.bicycle.BicycleUtils;

public class CustomBicycleUtils {

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
