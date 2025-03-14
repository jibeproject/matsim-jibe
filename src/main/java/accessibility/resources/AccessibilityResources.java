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
import routing.disutility.JibeDisutility3;
import routing.travelTime.BicycleTravelTime;
import routing.travelTime.WalkTravelTime;
import trip.Purpose;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
    private Boolean fwd;

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
                    String stressThreshold = properties.getProperty(AccessibilityProperties.CYCLE_STRESS_THRESHOLD);
                    if(stressThreshold != null) {
                        ((BicycleTravelTime) instance.tt).setLinkStressThreshold(stressThreshold);
                    }
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

            // Direction
            String input = properties.getProperty(AccessibilityProperties.FORWARD);
            if(input == null) {
                instance.fwd = null;
            } else if(input.equalsIgnoreCase("true")) {
                instance.fwd = true;
            } else if(input.equalsIgnoreCase("false")) {
                instance.fwd = false;
            } else {
                throw new RuntimeException("Unknown value " + input + " given for forward property. Must be \"true\", \"false\", or left out for a two-way analysis.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setActiveDisutility() {
        String type = properties.getProperty(AccessibilityProperties.IMPEDANCE);
        boolean dayOverride = false;
        switch(type) {
            case "shortest":
            case "short":
                td = new DistanceDisutility();
                break;
            case "fastest":
            case "fast":
                td = new OnlyTimeDependentTravelDisutility(tt);
                break;
            case "jibe_day":
                dayOverride = true;
            case "jibe_night":
                td = new JibeDisutility3(mode,tt,dayOverride);
                break;
            default:
                throw new RuntimeException("Disutility type " + type + " not recognised for mode " + mode);
        }
    }

    public synchronized String getMode() {
        return this.mode;
    }

    public synchronized Boolean fwdCalculation() { return this.fwd; }

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

    public synchronized int getInt(String key) {
        String value = properties.getProperty(key);
        return Integer.parseInt(value);
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

    public synchronized List<String> getStringList(String key) {
        ArrayList<String> strings = new ArrayList<>();

        String onlyString = properties.getProperty(key);
        if(onlyString != null) {
            // Case 1: only single item (i.e., non-numbered)
            strings.add(onlyString);
        } else {
            // Case 2: multiple numbered items (starting from 0)
            int counter = 0;
            String nextString = properties.getProperty(key + "." + counter);
            while(nextString != null) {
                strings.add(nextString);
                counter++;
                nextString = properties.getProperty(key + "." + counter);
            }
        }
        return strings;
    }

}
