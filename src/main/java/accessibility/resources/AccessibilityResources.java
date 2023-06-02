package accessibility.resources;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import routing.Bicycle;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;
import trip.Purpose;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AccessibilityResources {

    public static AccessibilityResources instance;
    private final Properties properties;
    private final Path baseDirectory;

    private Config config;
    private String mode;
    private Vehicle veh;
    private TravelTime tt;
    private TravelDisutility td;



    public AccessibilityResources(Properties properties, String baseDirectory) {
        this.properties = properties;
        this.baseDirectory = Paths.get(baseDirectory).getParent();
    }


    public static void initializeResources(String propertiesFile) {
        try (FileInputStream in = new FileInputStream(propertiesFile)) {
            Properties properties = new Properties();
            properties.load(in);
            instance = new AccessibilityResources(properties, propertiesFile);

            // Config
            instance.config = ConfigUtils.createConfig();

            // Get mode
            instance.mode = properties.getProperty(AccessibilityProperties.MODE);

            // Vehicle, travelTime, and TravelDisutility
            switch (instance.mode) {
                case TransportMode.bike:
                    Bicycle bicycle = new Bicycle(instance.config);
                    instance.veh = bicycle.getVehicle();
                    instance.tt = bicycle.getTravelTime();
                    instance.setActiveDisutility();
                    break;
                case TransportMode.walk:
                    instance.veh = null;
                    instance.tt = new WalkTravelTime();
                    instance.setActiveDisutility();
                    break;
                case TransportMode.car:
                    FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(instance.config.planCalcScore());
                    instance.veh = null;
                    instance.tt = freeSpeed;
                    instance.td = freeSpeed;
                    break;
                default:
                    throw new RuntimeException("Mode " + instance.mode + " not supported for accessibility calculations!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setActiveDisutility() {
        String type = properties.getProperty(AccessibilityProperties.DISUTILITY);
        switch(type) {
            case "shortest":
            case "short":
                td = new DistanceDisutility();
                break;
            case "fastest":
            case "fast":
                td = new OnlyTimeDependentTravelDisutility(tt);
                break;
            case "jibe":
                double mcTime = getMarginalCostOrDefault(mode, resources.Properties.TIME);
                double mcDist = getMarginalCostOrDefault(mode, resources.Properties.DISTANCE);
                double mcGrad = getMarginalCostOrDefault(mode, resources.Properties.GRADIENT);
                double mcComfort = getMarginalCostOrDefault(mode, resources.Properties.COMFORT);
                double mcAmbience = getMarginalCostOrDefault(mode, resources.Properties.AMBIENCE);
                double mcStress = getMarginalCostOrDefault(mode, resources.Properties.STRESS);
                td = new JibeDisutility(mode,tt,mcTime,mcDist,mcGrad,mcComfort,mcAmbience,mcStress);
                break;
            default:
                throw new RuntimeException("Disutility type " + type + " not recognised for mode " + mode);
        }
    }

    private synchronized double getMarginalCostOrDefault(String mode, String type) {
        String value = properties.getProperty("mc." + "." + type);
        if(value != null) {
            return Double.parseDouble(value);
        } else {
            return resources.Resources.instance.getMarginalCost(mode,type);
        }
    }

    public synchronized String getMode() {
        return this.mode;
    }

    public synchronized Vehicle getVehicle() {
        return this.veh;
    }

    public synchronized TravelTime getTravelTime() {
        return this.tt;
    }

    public synchronized TravelDisutility getTravelDisutility() {
        return this.td;
    }

    public synchronized String getString(String key) {
        return properties.getProperty(key);
    }

    public synchronized double getDouble(String key) {
        String value = properties.getProperty(key);
        return value != null ? Double.parseDouble(value) : Double.NaN;
    }

    public synchronized Purpose.PairList getPurposePairs() {
        int counter = 1;
        String nextPair = properties.getProperty(AccessibilityProperties.PURPOSE_PAIR + "." + counter);
        if(nextPair == null) {
            return null;
        }
        Purpose.PairList list = new Purpose.PairList();
        while (nextPair != null) {
            String[] purposes = nextPair.split(";");
            if(purposes.length != 2) {
                throw new RuntimeException("ACCESSIBILITY PROPERTIES ERROR: Incorrect number of purposes given for purpose pair " + counter);
            }
            Purpose startPurpose = Purpose.valueOf(purposes[0].toUpperCase());
            Purpose endPurpose = Purpose.valueOf(purposes[1].toUpperCase()); // todo: error if not recognised
            list.addPair(startPurpose,endPurpose);
            counter++;
            nextPair = properties.getProperty(AccessibilityProperties.PURPOSE_PAIR + "." + counter);
        }
        return list;
    }
}
