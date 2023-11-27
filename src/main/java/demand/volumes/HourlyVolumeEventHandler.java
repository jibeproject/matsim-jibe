package demand.volumes;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

public class HourlyVolumeEventHandler implements LinkEnterEventHandler {

    Vehicles vehicles = VehicleUtils.createVehiclesContainer();

    public HourlyVolumeEventHandler(String vehiclesFile) {
        MatsimVehicleReader.VehicleReader reader = new MatsimVehicleReader.VehicleReader(vehicles);
        reader.readFile(vehiclesFile);
    }

    private final IdMap<Link, int[]> carVolumes = new IdMap<>(Link.class);
    private final IdMap<Link, int[]> truckVolumes = new IdMap<>(Link.class);

    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id<Link> linkId = event.getLinkId();

        String vehicleType = vehicles.getVehicles().get(event.getVehicleId()).getType().getId().toString();

        int hour = ((int) (event.getTime() / 3600.)) % 24;

        String mode = event.getAttributes().get("networkMode");

        if(vehicleType.equals("car")) {
            int[] linkVolumes = carVolumes.getOrDefault(linkId,new int[24]);
            linkVolumes[hour]++;
            carVolumes.put(linkId,linkVolumes);
        } else if (vehicleType.equals("truck")) {
            int[] linkVolumes = truckVolumes.getOrDefault(linkId,new int[24]);
            linkVolumes[hour]++;
            truckVolumes.put(linkId, linkVolumes);
        } else {
            throw new RuntimeException("Unrecognised vehicle type " + mode);
        }
    }

    public IdMap<Link, int[]> getCarVolumes() {
        return carVolumes;
    }

    public IdMap<Link, int[]> getTruckVolumes() {
        return truckVolumes;
    }
}
