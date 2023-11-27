package routing;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

public interface TravelAttribute {
    //double getTravelAttribute(Link var1);
    double getTravelAttribute(Link var1, TravelDisutility var2, TravelTime var3);
}
