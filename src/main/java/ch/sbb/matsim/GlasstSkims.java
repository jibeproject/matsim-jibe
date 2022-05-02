package ch.sbb.matsim;

import ch.sbb.matsim.analysis.CalculateData;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;

import java.io.IOException;

public class GlasstSkims {

    private final static Logger log = Logger.getLogger(GlasstSkims.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 6) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Network File Path \n" +
                    "(1) Zone coordinates File \n" +
                    "(2) Public Transport Network File Path \n" +
                    "(3) Public Transport Schedule File Path \n" +
                    "(4) Output File Path \n" +
                    "(5) Number of Threads \n");
        }

        String networkPath = args[0];
        String zoneCoordinatesFile = args[1];
        String ptNetworkPath = args[2];
        String ptSchedulePath = args[3];
        String outputDirectory = args[4];
        int numberOfThreads = Integer.parseInt(args[5]);

        // Setup Config
        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);

        // Setup Indicator Calculator
        CalculateData calc = new CalculateData(outputDirectory,numberOfThreads, null);
        calc.loadSamplingPointsFromFile(zoneCoordinatesFile);

        // CAR
        FreespeedTravelTimeAndDisutility freeSpeed = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        calc.calculateRouteIndicators(networkPath,config,freeSpeed,freeSpeed,"car_",
                null, TransportMode.car,
                l -> !((boolean) l.getAttributes().getAttribute("motorway")));

        // PUBLIC TRANSPORT CALCULATIONS (WORK IN PROGRESS)
        calc.calculatePtIndicators(ptNetworkPath,ptSchedulePath,28200,29400,config,"pt_",(l, r) -> true);
    }

}
