package trip;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import java.util.List;

public class Route {
    private final List<Id<Link>> linkIds;
    private final double distance;

    public Route(List<Id<Link>> linkIds, double distance) {
        this.linkIds = linkIds;
        this.distance = distance;
    }

    public List<Id<Link>> getLinkIds() {
        return this.linkIds;
    }

    public double getDistance() {
        return this.distance;
    }
}
