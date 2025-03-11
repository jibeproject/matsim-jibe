package demand;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import resources.Properties;
import resources.Resources;

public class RunSimulation {
    public static void main(String[] args) {
        if (args.length>2) {
            throw new RuntimeException("Program requires 2 arguments:\n" +
                    "(0) Properties file \n" +
                    "(1) Simulation output directory [optional] \n");
        }

        Resources.initializeResources(args[0]);

        Config config = ConfigUtils.loadConfig(Resources.instance.getString(Properties.MATSIM_DEMAND_CONFIG));

        // Set network, plans, vehicles
        config.network().setInputFile(Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK));
        config.plans().setInputFile(Resources.instance.getString(Properties.MATSIM_DEMAND_PLANS));
        config.vehicles().setVehiclesFile(Resources.instance.getString(Properties.MATSIM_DEMAND_VEHICLES));

        // Set threads
        config.global().setNumberOfThreads(Resources.instance.getInt(Properties.NUMBER_OF_THREADS));
        config.qsim().setNumberOfThreads(Resources.instance.getInt(Properties.NUMBER_OF_THREADS) / 2);

        // Set scale factor
        double scaleFactor = Resources.instance.getDouble(Properties.MATSIM_DEMAND_SCALE_FACTOR);
        config.qsim().setFlowCapFactor(scaleFactor);
        config.qsim().setStorageCapFactor(scaleFactor);

        // Set output directory
        if(args.length == 2){
            config.controler().setOutputDirectory(args[1]);
        }

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Controler controler = new Controler(scenario);
        controler.run();
    }
}
