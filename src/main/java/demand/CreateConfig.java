package demand;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import resources.Properties;
import resources.Resources;

import java.util.Set;

public class CreateConfig {
    public static void main(String[] args) {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Scale factor");
        }

        Resources.initializeResources(args[0]);
        double scaleFactor = Double.parseDouble(args[1]);

        Config config = ConfigUtils.createConfig();
        config.controler().setLastIteration(5);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

        // Specify input network file
        config.network().setInputFile(Resources.instance.getString(Properties.MATSIM_CAR_NETWORK));

        // Specify vehicles file
        config.vehicles().setVehiclesFile("/home/corin/IdeaProjects/matsim-jibe/src/main/java/demand/mode-vehicles.xml");

        // Specify population
        Population population = PopulationUtils.createPopulation(config);
        PopulationUtils.readPopulation(population, Resources.instance.getString(Properties.MATSIM_TFGM_PLANS));
        PopulationUtils.sampleDown(population,scaleFactor);
        PopulationUtils.writePopulation(population,"temp.xml");
        config.plans().setInputFile("temp.xml");

        // Specify input network and plans files
        config.qsim().setFlowCapFactor(scaleFactor);
        config.qsim().setMainModes(Set.of("car","truck"));
        config.qsim().setStorageCapFactor(scaleFactor);
        config.qsim().setLinkDynamics(QSimConfigGroup.LinkDynamics.PassingQ);
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        // Router
        config.plansCalcRoute().setNetworkModes(Set.of("car","truck"));

        // Route
        PlanCalcScoreConfigGroup.ActivityParams activity = new PlanCalcScoreConfigGroup.ActivityParams("loc");
        activity.setTypicalDuration(23 * 60 * 60);
        config.planCalcScore().addActivityParams(activity);

        // define strategies:
        {
            StrategyConfigGroup.StrategySettings strat = new StrategyConfigGroup.StrategySettings();
            strat.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute);
            strat.setWeight(0.15);
            config.strategy().addStrategySettings(strat);
        }
        {
            StrategyConfigGroup.StrategySettings strat = new StrategyConfigGroup.StrategySettings();
            strat.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta);
            strat.setWeight(0.9);

            config.strategy().addStrategySettings(strat);
        }

        config.strategy().setMaxAgentPlanMemorySize(5);
        config.strategy().setFractionOfIterationsToDisableInnovation(0.9);

        config.vspExperimental().setWritingOutputEvents(true);



        ConfigUtils.writeMinimalConfig(config,"/home/corin/IdeaProjects/matsim-jibe/src/main/java/demand/config.xml");
    }
}
