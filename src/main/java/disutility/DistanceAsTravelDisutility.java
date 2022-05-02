package disutility;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.vehicles.Vehicle;

public class DistanceAsTravelDisutility implements TravelDisutility {

    public DistanceAsTravelDisutility() {
    }

    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
        return link.getLength();
    }

    public double getLinkMinimumTravelDisutility(Link link) {
        return link.getLength();
    }

}