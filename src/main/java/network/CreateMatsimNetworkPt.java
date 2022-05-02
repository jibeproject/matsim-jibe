package network;

import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.run.PublicTransitMapper;

// Required input in GTFS format:
// For bus & tram network use https://data.bus-data.dft.gov.uk/downloads/
// For rail network use http://data.atoc.org/
// Rail can be converted to GTFS using the UK2GTFS R package here https://itsleeds.github.io/UK2GTFS/index.html

public class CreateMatsimNetworkPt {

    public static void main(String[] args) {

        if(args.length != 11) {
            throw new RuntimeException("Program requires 11 arguments: \n" +
                    "(0) GTFS directory for bus/tram \n" +
                    "(1) GTFS directory for rail \n" +
                    "(2) Input schedule file for bus/tram \n" +
                    "(3) Input schedule file for rail \n" +
                    "(4) Input network file \n" +
                    "(5) Output schedule file for bus/tram \n" +
                    "(6) Output schedule file for rail \n" +
                    "(7) Output network file for bus/tram \n" +
                    "(8) Output network file for rail \n" +
                    "(9) Config file \n" +
                    "(10) Number of Threads");
        }

        final String gtfsBusTram = args[0];
        final String gtfsRail = args[1];
        final String scheduleBusTram = args[2];
        final String scheduleRail = args[3];
        final String inputNetwork = args[4];
        final String outputScheduleBusTram = args[5];
        final String outputScheduleRail = args[6];
        final String outputNetworkBusTram = args[7];
        final String outputNetworkRail = args[8];
        final String configFile = args[9];
        final int numberOfThreads = Integer.parseInt(args[10]);

        // todo: Combine bus and rail

        // Convert GTFS to Transit Schedule (bus + tram)
        Gtfs2TransitSchedule.run(gtfsBusTram,"dayWithMostTrips","EPSG:27700", scheduleBusTram,null);

        // Convert GTFS to Transit Schedule (rail)
        Gtfs2TransitSchedule.run(gtfsRail,"dayWithMostTrips","EPSG:27700", scheduleRail,null);

        // Create PT Mapper Config
        PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
        config.setInputScheduleFile(scheduleBusTram);
        config.setInputNetworkFile(inputNetwork);
        config.setOutputScheduleFile(outputScheduleBusTram);
        config.setOutputNetworkFile(outputNetworkBusTram);
        config.setNumOfThreads(numberOfThreads);
        config.getModesToKeepOnCleanUp().add("walk");
//
//        PublicTransitMappingConfigGroup.TransportModeAssignment tmaBus = new PublicTransitMappingConfigGroup.TransportModeAssignment("bus");
//        tmaBus.setNetworkModesStr("");
//        PublicTransitMappingConfigGroup.TransportModeAssignment tmaTram = new PublicTransitMappingConfigGroup.TransportModeAssignment("tram");
//        tmaBus.setNetworkModesStr("");
//        PublicTransitMappingConfigGroup.TransportModeAssignment tmaRail = new PublicTransitMappingConfigGroup.TransportModeAssignment("rail");
//        tmaBus.setNetworkModesStr("");
//        config.addParameterSet(tmaBus);

        config.writeToFile(configFile);

        // Run PT Mapper
        PublicTransitMapper.run(configFile);
    }


}

