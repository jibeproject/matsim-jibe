package demand.volumes;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.vehicles.*;

public class DailyVolumeEventHandler implements LinkEnterEventHandler {

    Vehicles vehicles = VehicleUtils.createVehiclesContainer();

    public DailyVolumeEventHandler(String vehiclesFile) {
        MatsimVehicleReader.VehicleReader reader = new MatsimVehicleReader.VehicleReader(vehicles);
        reader.readFile(vehiclesFile);
    }

    private final IdMap<Link, Integer> carVolumes = new IdMap<>(Link.class);
    private final IdMap<Link, Integer> truckVolumes = new IdMap<>(Link.class);
    private final IdMap<Link, Integer> adjVolumes = new IdMap<>(Link.class);

    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id<Link> linkId = event.getLinkId();

        String vehicleType = vehicles.getVehicles().get(event.getVehicleId()).getType().getId().toString();

        String mode = event.getAttributes().get("networkMode");

        if(vehicleType.equals("car")) {
            carVolumes.put(linkId, carVolumes.getOrDefault(linkId,0) + 1);
            adjVolumes.put(linkId, adjVolumes.getOrDefault(linkId,0) + 1);
        } else if (vehicleType.equals("truck")) {
            truckVolumes.put(linkId, truckVolumes.getOrDefault(linkId,0) + 1);
            adjVolumes.put(linkId, adjVolumes.getOrDefault(linkId,0) + 6);
        } else {
            throw new RuntimeException("Unrecognised vehicle type " + mode);
        }
    }

    public IdMap<Link, Integer> getCarVolumes() {
        return carVolumes;
    }

    public IdMap<Link, Integer> getTruckVolumes() {
        return truckVolumes;
    }

    public IdMap<Link, Integer> getAdjVolumes() {
        return adjVolumes;
    }
}
