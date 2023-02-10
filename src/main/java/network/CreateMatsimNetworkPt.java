package network;

import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.run.PublicTransitMapper;
import org.matsim.pt2matsim.tools.ScheduleTools;

// Required input in GTFS format:
// For bus & tram network use https://data.bus-data.dft.gov.uk/downloads/
// For rail network use http://data.atoc.org/
// Rail schedule can be converted to GTFS using the UK2GTFS R package here https://itsleeds.github.io/UK2GTFS/index.html

public class CreateMatsimNetworkPt {

    public static void main(String[] args) {

        if(args.length != 8) {
            throw new RuntimeException("Program requires 8 arguments: \n" +
                    "(0) GTFS directory for bus/tram \n" +
                    "(1) GTFS directory for rail \n" +
                    "(2) Unmapped schedule file \n" +
                    "(3) Input network file \n" +
                    "(4) Output (mapped) schedule file \n" +
                    "(5) Output network file \n" +
                    "(6) Config file \n" +
                    "(7) Number of Threads");
        }

        final String gtfsBusTram = args[0];
        final String gtfsRail = args[1];
        final String scheduleFile = args[2];
        final String inputNetwork = args[3];
        final String outputScheduleMapped = args[4];
        final String outputNetwork = args[5];
        final String configFile = args[6];
        final int numberOfThreads = Integer.parseInt(args[7]);

        // Setup GTFS converters
        GtfsConverter converter1 = new GtfsConverter(new GtfsFeedImpl(gtfsBusTram));
        GtfsConverter converter2 = new GtfsConverter(new GtfsFeedImpl(gtfsRail));

        converter1.convert("dayWithMostTrips","EPSG:27700");
        converter2.convert("dayWithMostTrips","EPSG:27700");

        // Create combined bus/tram/rail schedule
        TransitSchedule schedule = converter1.getSchedule();
        ScheduleTools.mergeSchedules(schedule,converter2.getSchedule());

        // Write combined schedule
        ScheduleTools.writeTransitSchedule(schedule,scheduleFile);

        // Create PT Mapper Config
        PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
        config.setInputScheduleFile(scheduleFile);
        config.setInputNetworkFile(inputNetwork);
        config.setOutputScheduleFile(outputScheduleMapped);
        config.setOutputNetworkFile(outputNetwork);
        config.setNumOfThreads(numberOfThreads);
//        config.getModesToKeepOnCleanUp().add("walk");

        PublicTransitMappingConfigGroup.TransportModeAssignment tmaBus = new PublicTransitMappingConfigGroup.TransportModeAssignment("bus");
        tmaBus.setNetworkModesStr("car,bus");
        config.addParameterSet(tmaBus);



        config.writeToFile(configFile);

        // Run PT Mapper
        PublicTransitMapper.run(configFile);
    }


}

