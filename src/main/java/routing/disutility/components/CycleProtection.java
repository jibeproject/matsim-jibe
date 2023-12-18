package routing.disutility.components;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.bicycle.BicycleUtils;

public enum CycleProtection {
    OFFROAD,
    PROTECTED,
    LANE,
    MIXED;

    public static CycleProtection getType(Link link) {
        String cycleosm = (String) link.getAttributes().getAttribute("cycleosm");
        String cycleway = (String) link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY);

        if (link.getAllowedModes().contains(TransportMode.walk) || link.getAllowedModes().contains(TransportMode.bike)) {
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
                    switch (cycleway) {
                        case "track":
                            return PROTECTED;
                        case "share_busway":
                        case "lane":
                            return LANE;
                        default:
                            return MIXED;
                    }
            }
        } else {
            return null;
        }
    }

}
