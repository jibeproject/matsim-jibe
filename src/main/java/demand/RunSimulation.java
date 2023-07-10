package demand;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PopulationUtils;
import resources.Properties;
import resources.Resources;

public class RunSimulation {
    public static void main(String[] args) {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Scale factor");
        }

        Resources.initializeResources(args[0]);
        double scaleFactor = Double.parseDouble(args[1]);

        Config config = ConfigUtils.createConfig();
        config.controler().setLastIteration(200);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

        // Specify input network file
        config.network().setInputFile(Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK));

        // Specify population
        Population population = PopulationUtils.createPopulation(config);
        PopulationUtils.readPopulation(Resources.instance.getString(Properties.MATSIM_TFGM_PLANS));
        PopulationUtils.sampleDown(population,scaleFactor);

        // Specify input network and plans files
        config.qsim().setFlowCapFactor(scaleFactor);
        config.qsim().setStorageCapFactor(scaleFactor);

        PlanCalcScoreConfigGroup.ActivityParams activity = new PlanCalcScoreConfigGroup.ActivityParams("loc");
        activity.setTypicalDuration(23 * 60 * 60);
        config.planCalcScore().addActivityParams(activity);

    }


}
