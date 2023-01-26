package accessibility;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.WalkTravelTime;
import routing.travelTime.speed.BicycleLinkSpeedCalculatorDefaultImpl;

public class AccessibilityData {

    private final static double MAX_BIKE_SPEED = 16 / 3.6;

    final Config config;
    final String mode;
    final TravelDisutility disutility;
    final Vehicle vehicle;

    private AccessibilityData(Config config, String mode, TravelDisutility disutility, Vehicle vehicle) {
        this.config = config;
        this.mode = mode;
        this.disutility = disutility;
        this.vehicle = vehicle;
    }

    public static AccessibilityData WalkJibeAccessibility() {
        TravelTime ttWalk = new WalkTravelTime();
        TravelDisutility tdWalkJibe = new JibeDisutility(TransportMode.walk,ttWalk);
        return new AccessibilityData(null, TransportMode.walk, tdWalkJibe, null);
    }

    public static AccessibilityData WalkDistAccessibility() {
        TravelDisutility tdDist = new DistanceDisutility();
        return new AccessibilityData(null, TransportMode.walk, tdDist, null);
    }

    public static AccessibilityData BikeJibeAccessibility() {

        // Set up config
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode(TransportMode.bike);
        config.addModule(bicycleConfigGroup);

        // Bike vehicle and travel time
        VehicleType type = VehicleUtils.createVehicleType(Id.create("routing", VehicleType.class));
        type.setMaximumVelocity(MAX_BIKE_SPEED);
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        Vehicle bike = VehicleUtils.createVehicle(Id.createVehicleId(1), type);
        TravelTime ttBike = new BicycleTravelTime(linkSpeedCalculator);

        // Impedance
        TravelDisutility tdBikeJibe = new JibeDisutility(TransportMode.bike,ttBike);

        return new AccessibilityData(config, TransportMode.bike, tdBikeJibe, bike);
    }

    public AccessibilityData BikeDistAccessibility() {
        Config config = ConfigUtils.createConfig();
        VehicleType type = VehicleUtils.createVehicleType(Id.create("routing", VehicleType.class));
        type.setMaximumVelocity(MAX_BIKE_SPEED);
        BicycleLinkSpeedCalculatorDefaultImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorDefaultImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        Vehicle bike = VehicleUtils.createVehicle(Id.createVehicleId(1), type);

        return new AccessibilityData(config, TransportMode.bike, new DistanceDisutility(), bike);
    }

}
