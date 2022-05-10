package bicycle.jibe;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import static bicycle.jibe.CycleSafety.*;

public class SafeRoute implements TravelDisutility {

    public static final double AMBER_DISUTILITY = 1000;
    public static final double RED_DISUTILITY = 100;

    final TravelDisutility baseDisutility;
    double marginalAmberCost;
    double marginalRedCost;


    public SafeRoute(BicycleConfigGroup bicycleConfigGroup, PlanCalcScoreConfigGroup cnScoringGroup, TravelTime timeCalculator,
                     double marginalAmberCost, double marginalRedCost) {

        this.baseDisutility = new CustomBicycleDisutility(bicycleConfigGroup,cnScoringGroup,timeCalculator);
        this.marginalAmberCost = marginalAmberCost;
        this.marginalRedCost = marginalRedCost;

    }

    @Override
    public double getLinkTravelDisutility(Link link, double v, Person person, Vehicle vehicle) {

        double disutility = baseDisutility.getLinkTravelDisutility(link,v,person,vehicle);


        CycleSafety safety = CustomBicycleUtils.getLinkSafety(link);

        if(safety.equals(AMBER)) {
            disutility += AMBER_DISUTILITY * link.getLength();
        } else if (safety.equals(RED)) {
            disutility += RED_DISUTILITY * link.getLength();
        }

        return disutility;
    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }
}
