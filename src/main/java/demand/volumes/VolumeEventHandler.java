package demand.volumes;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;

public class VolumeEventHandler implements LinkEnterEventHandler {

    public IdMap<Link, Integer> getLinkVolumes() {
        return linkVolumes;
    }
    private final IdMap<Link, Integer> linkVolumes = new IdMap<>(Link.class);
    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id<Link> linkId = event.getLinkId();
        linkVolumes.put(linkId, linkVolumes.getOrDefault(linkId,0) + 1);
    }

}
