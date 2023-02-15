package resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Resources {

    public static Resources instance;
    private final Properties properties;
    private final Path baseDirectory;

    private Resources(Properties properties, String baseDirectory) {
        this.properties = properties;
        this.baseDirectory = Paths.get(baseDirectory).getParent();
    }

    public static void initializeResources(String propertiesFile) {
        try (FileInputStream in = new FileInputStream(propertiesFile)) {
            Properties properties = new Properties();
            properties.load(in);
            instance = new Resources(properties, propertiesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized String getString(String key) {
        return properties.getProperty(key);
    }

    public synchronized int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public synchronized File getFile(String key) {
        return new File(properties.getProperty(key));
    }

    public synchronized double getDouble(String key) {
        return Double.parseDouble(properties.getProperty(key));
    }

    public synchronized double getMarginalCost(String mode, String type) {
        return getDouble("mc." + mode + "." + type);
    }





}
