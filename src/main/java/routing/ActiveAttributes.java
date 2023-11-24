package routing;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.vehicles.Vehicle;
import routing.disutility.JibeDisutility;
import routing.disutility.JibeDisutility3;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkComfort;
import routing.disutility.components.LinkStress;

import java.util.LinkedHashMap;

public class ActiveAttributes {

    public static LinkedHashMap<String,TravelAttribute> get(String mode) {
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("vgvi",(l,td) -> LinkAmbience.getVgviFactor(l) * l.getLength());
        attributes.put("lighting",(l,td) -> LinkAmbience.getLightingFactor(l) * l.getLength());
        attributes.put("shannon", (l,td) -> LinkAmbience.getShannonFactor(l) * l.getLength());
        attributes.put("crime", (l,td) -> LinkAmbience.getCrimeFactor(l) * l.getLength());
        attributes.put("POIs",(l,td) -> LinkAmbience.getPoiFactor(l) * l.getLength());
        attributes.put("negPOIs",(l,td) -> LinkAmbience.getNegativePoiFactor(l) * l.getLength());
        attributes.put("freightPOIs",(l,td) -> LinkStress.getFreightPoiFactor(l) * l.getLength());
        attributes.put("ambience", (l,td) -> LinkAmbience.getDayAmbience(l) * l.getLength());
        attributes.put("stressLink",(l,td) -> LinkStress.getStress(l,mode) * l.getLength());
        attributes.put("stressJct",(l,td) -> JctStress.getStress(l,mode));
        return attributes;
    }

    public static LinkedHashMap<String,TravelAttribute> getJibe(String mode, Vehicle veh) {
        LinkedHashMap<String,TravelAttribute> attributes = get(mode);
        attributes.put("c_time",(l,td) -> ((JibeDisutility) td).getTimeComponent(l,0.,null,veh));
        attributes.put("c_dist",(l,td) -> ((JibeDisutility) td).getDistanceComponent(l));
        attributes.put("c_grad",(l,td) -> ((JibeDisutility) td).getGradientComponent(l));
        attributes.put("c_surf",(l,td) -> ((JibeDisutility) td).getSurfaceComponent(l));
        attributes.put("c_attr",(l,td) -> ((JibeDisutility) td).getAmbienceComponent(l));
        attributes.put("c_stress",(l,td) -> ((JibeDisutility) td).getStressComponent(l));
        attributes.put("c_jct",(l,td) -> ((JibeDisutility) td).getJunctionComponent(l));
        return attributes;
    }

    public static LinkedHashMap<String,TravelAttribute> getJibe3(String mode, Vehicle veh) {
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("gradient",(l,td) -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.) * getJibe3TravelTime(l,td,veh));
        attributes.put("comfort",(l,td) -> LinkComfort.getComfortFactor(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("vgvi",(l,td) -> LinkAmbience.getVgviFactor(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("lighting",(l,td) -> LinkAmbience.getLightingFactor(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("shannon", (l,td) -> LinkAmbience.getShannonFactor(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("crime", (l,td) -> LinkAmbience.getCrimeFactor(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("POIs",(l,td) -> LinkAmbience.getPoiFactor(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("negPOIs",(l,td) -> LinkAmbience.getNegativePoiFactor(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("ambience", (l,td) -> LinkAmbience.getDayAmbience(l) * getJibe3TravelTime(l,td,veh));
        attributes.put("stressLink",(l,td) -> LinkStress.getStress(l,mode) * getJibe3TravelTime(l,td,veh));
        attributes.put("stressJct",(l,td) -> getJibe3StressJct(mode,l,td,veh));
        return attributes;
    }

    private static double getJibe3TravelTime(Link l, TravelDisutility td, Vehicle veh) {
        return ((JibeDisutility3) td).getLinkTravelTime(l,0.,null,veh);
    }

    private static  double getJibe3StressJct(String mode, Link l, TravelDisutility td, Vehicle veh) {
        if((boolean) l.getAttributes().getAttribute("crossVehicles")) {
            return JctStress.getStress(l,mode) * getJibe3TravelTime(l,td,veh) *
                    (Math.min((double) l.getAttributes().getAttribute("crossWidth") / l.getLength(), 1.));
        } else return 0.;
    }


}
