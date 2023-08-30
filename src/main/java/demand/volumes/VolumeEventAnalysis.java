package demand.volumes;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.EventsUtils;



public class VolumeEventAnalysis {



    public static void main(String[] args) {

        // Read in args and pass to the below function...
    }

    public static void addVolumesToNetwork(Network network, String attributeName, String eventsFilePath) {
        EventsManager eventsManager = new EventsManagerImpl();
        VolumeEventHandler volumeEventHandler = new VolumeEventHandler();
        eventsManager.addHandler(volumeEventHandler);
        EventsUtils.readEvents(eventsManager,eventsFilePath);
        volumeEventHandler.getLinkVolumes().forEach((id,vol) -> network.getLinks().get(id).getAttributes().putAttribute(attributeName,vol));
    }

}
