package demand;

import demand.volumes.HourlyVolumeEventHandler;
import io.ioUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.EventsUtils;
import resources.Properties;
import resources.Resources;

import java.io.File;
import java.io.PrintWriter;

public class WriteHourlyVolumes {

    private final static Logger log = Logger.getLogger(WriteHourlyVolumes.class);

    public static void main(String[] args) {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output csv file");
        }

        Resources.initializeResources(args[0]);
        String outputCsv = args[1];

        Network network = NetworkUtils2.readFullNetwork();

        log.info("Estimating volumes from events...");
        int scaleFactor = (int) (1 / Resources.instance.getDouble(Properties.MATSIM_DEMAND_OUTPUT_SCALE_FACTOR));
        log.info("Multiplying all volumes from events file by a factor of " + scaleFactor);

        EventsManager eventsManager = new EventsManagerImpl();
        HourlyVolumeEventHandler hourlyVolumeEventHandler = new HourlyVolumeEventHandler(Resources.instance.getString(Properties.MATSIM_DEMAND_OUTPUT_VEHICLES));
        eventsManager.addHandler(hourlyVolumeEventHandler);
        EventsUtils.readEvents(eventsManager,Resources.instance.getString(Properties.MATSIM_DEMAND_OUTPUT_EVENTS));

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv),false);
        assert out != null;

        log.info("Writing csv...");
        // Write header
        out.println("linkId,edgeId,osmId,hour,cars,trucks");

        // Write table
        for(Link link : network.getLinks().values()) {
            String linkId = link.getId().toString();
            int edgeId = (int) link.getAttributes().getAttribute("edgeID");
            int osmId = (int) link.getAttributes().getAttribute("osmID");
            int[] carVolumes = hourlyVolumeEventHandler.getCarVolumes().getOrDefault(link.getId(),new int[24]);
            int[] truckVolumes = hourlyVolumeEventHandler.getTruckVolumes().getOrDefault(link.getId(),new int[24]);

            for(int i = 0 ; i < 24 ; i++) {
                out.println(linkId + "," + edgeId + "," + osmId + "," + i + "," +
                        (carVolumes[i] * scaleFactor) + "," + (truckVolumes[i] * scaleFactor));
            }
        }

        // Close
        out.close();

    }
}
