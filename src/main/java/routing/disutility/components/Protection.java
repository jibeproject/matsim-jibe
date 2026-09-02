package routing.disutility.components;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.bicycle.BicycleUtils;

public enum Protection {
    KERBED,
    PROTECTED,
    LANE,
    MIXED;

    public static Protection getType(Link link) {
        String cycleosm = (String) link.getAttributes().getAttribute("cycleosm");
        String cycleway = (String) link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY);

        if (link.getAllowedModes().contains(TransportMode.walk) || link.getAllowedModes().contains(TransportMode.bike)) {
            return switch (cycleosm) {
                case "offroad", "kerbed" -> KERBED;
                case "protected" -> PROTECTED;
                case "painted" -> LANE;
                case "integrated" -> MIXED;
                default -> switch (cycleway) {
                    case "track" -> PROTECTED;
                    case "share_busway", "lane" -> LANE;
                    default -> MIXED;
                };
            };
        } else {
            return null;
        }
    }

}
