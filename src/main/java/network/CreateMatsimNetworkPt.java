package network;

import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.run.PublicTransitMapper;
import org.matsim.pt2matsim.tools.ScheduleTools;
import resources.Properties;
import resources.Resources;

// Required input in GTFS format:
// For bus & tram network use https://data.bus-data.dft.gov.uk/downloads/
// For rail network use http://data.atoc.org/
// Rail schedule can be converted to GTFS using the UK2GTFS R package here https://itsleeds.github.io/UK2GTFS/index.html

public class CreateMatsimNetworkPt {

    public static void main(String[] args) {

        if(args.length != 8) {
            throw new RuntimeException("Program requires 5 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) GTFS zip filepath for bus/tram \n" +
                    "(2) GTFS zip filepath for rail \n" +
                    "(3) Unmapped schedule file \n" +
                    "(4) Config file");
        }

        Resources.initializeResources(args[0]);

        final String inputNetwork = Resources.instance.getString(Properties.MATSIM_ROAD_NETWORK);
        final String scheduleMapped = Resources.instance.getString(Properties.MATSIM_TRANSIT_SCHEDULE);
        final String outputNetwork = Resources.instance.getString(Properties.MATSIM_TRANSIT_NETWORK);
        final int numberOfThreads = Resources.instance.getInt(Properties.NUMBER_OF_THREADS);

        final String gtfsBusTram = args[1];
        final String gtfsRail = args[2];
        final String scheduleUnmapped = args[3];
        final String configFile = args[4];

        // Setup GTFS converters
        GtfsConverter converter1 = new GtfsConverter(new GtfsFeedImpl(gtfsBusTram));
        GtfsConverter converter2 = new GtfsConverter(new GtfsFeedImpl(gtfsRail));

        converter1.convert("dayWithMostTrips",Resources.instance.getString(Properties.COORDINATE_SYSTEM));
        converter2.convert("dayWithMostTrips",Resources.instance.getString(Properties.COORDINATE_SYSTEM));

        // Create combined bus/tram/rail schedule
        TransitSchedule schedule = converter1.getSchedule();
        ScheduleTools.mergeSchedules(schedule,converter2.getSchedule());

        // Write combined schedule
        ScheduleTools.writeTransitSchedule(schedule,scheduleUnmapped);

        // Create PT Mapper Config
        PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
        config.setInputScheduleFile(scheduleUnmapped);
        config.setInputNetworkFile(inputNetwork);
        config.setOutputScheduleFile(scheduleMapped);
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

