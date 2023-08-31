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

        Resources.initializeResources(args[0]);

        Config config = ConfigUtils.loadConfig(Resources.instance.getString(Properties.MATSIM_TFGM_CONFIG));

        // Set network, plans, vehicles
        config.network().setInputFile(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));
        config.plans().setInputFile(Resources.instance.getString(Properties.MATSIM_TFGM_PLANS));
        config.vehicles().setVehiclesFile(Resources.instance.getString(Properties.MATSIM_TFGM_VEHICLES));

        // Set threads
        config.global().setNumberOfThreads(Resources.instance.getInt(Properties.NUMBER_OF_THREADS));
        config.qsim().setNumberOfThreads(Resources.instance.getInt(Properties.NUMBER_OF_THREADS) / 2);

        // Set scale factor
        double scaleFactor = Resources.instance.getDouble(Properties.MATSIM_TFGM_SCALE_FACTOR);
        config.qsim().setFlowCapFactor(scaleFactor);
        config.qsim().setStorageCapFactor(scaleFactor);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Controler controler = new Controler(scenario);
        controler.run();
    }
}
