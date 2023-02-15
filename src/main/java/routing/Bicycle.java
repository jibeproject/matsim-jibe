package routing;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import resources.Properties;
import resources.Resources;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;

public class Bicycle {

    private final TravelTime travelTime;
    private final Vehicle vehicle;

    public Bicycle(Config config) {

        double maxBikeSpeed = Resources.instance.getDouble(Properties.MAX_BIKE_SPEED);

        // Setup config
        if(config == null) {
            config = ConfigUtils.createConfig();
        }
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode("bike");
        config.addModule(bicycleConfigGroup);

        // Setup vehicle
        VehicleType type = VehicleUtils.createVehicleType(Id.create("bicycle", VehicleType.class));
        type.setMaximumVelocity(maxBikeSpeed);
        vehicle = VehicleUtils.createVehicle(Id.createVehicleId(1), type);

        // Setup travel time
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        travelTime = new BicycleTravelTime(linkSpeedCalculator);
    }

    public Vehicle getVehicle() {
        return this.vehicle;
    }

    public TravelTime getTravelTime() {
        return this.travelTime;
    }

}
