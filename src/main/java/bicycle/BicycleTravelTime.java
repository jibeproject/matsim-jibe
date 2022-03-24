package bicycle;

import com.google.inject.Inject;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.BicycleLinkSpeedCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

/**
 * @author dziemke
 */
public class BicycleTravelTime implements TravelTime {

    @Inject
    private BicycleLinkSpeedCalculator linkSpeedCalculator;

    @Inject
    public BicycleTravelTime(BicycleLinkSpeedCalculator calculator) {
        this.linkSpeedCalculator = calculator;
    }

    @Override
    public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {

        return link.getLength() / linkSpeedCalculator.getMaximumVelocityForLink(link, null);
    }
}
