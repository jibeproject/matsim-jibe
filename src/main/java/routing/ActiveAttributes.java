package routing;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import routing.disutility.JibeDisutility;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkComfort;
import routing.disutility.components.LinkStress;

import java.util.LinkedHashMap;

public class ActiveAttributes {

    public static LinkedHashMap<String,TravelAttribute> get(String mode) {
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("vgvi",(l,td,tt) -> LinkAmbience.getVgviFactor(l) * l.getLength());
        attributes.put("lighting",(l,td,tt) -> LinkAmbience.getLightingFactor(l) * l.getLength());
        attributes.put("shannon", (l,td,tt) -> LinkAmbience.getShannonFactor(l) * l.getLength());
        attributes.put("crime", (l,td,tt) -> LinkAmbience.getCrimeFactor(l) * l.getLength());
        attributes.put("POIs",(l,td,tt) -> LinkAmbience.getPoiFactor(l) * l.getLength());
        attributes.put("negPOIs",(l,td,tt) -> LinkAmbience.getNegativePoiFactor(l) * l.getLength());
        attributes.put("freightPOIs",(l,td,tt) -> LinkStress.getFreightPoiFactor(l) * l.getLength());
        attributes.put("ambience", (l,td,tt) -> LinkAmbience.getDayAmbience(l) * l.getLength());
        attributes.put("stressLink",(l,td,tt) -> LinkStress.getStress(l,mode) * l.getLength());
        attributes.put("stressJct",(l,td,tt) -> JctStress.getStress(l,mode));
        return attributes;
    }

    public static LinkedHashMap<String,TravelAttribute> getJibe(String mode, Vehicle veh) {
        LinkedHashMap<String,TravelAttribute> attributes = get(mode);
        attributes.put("c_time",(l,td,tt) -> ((JibeDisutility) td).getTimeComponent(l,0.,null,veh));
        attributes.put("c_dist",(l,td,tt) -> ((JibeDisutility) td).getDistanceComponent(l));
        attributes.put("c_grad",(l,td,tt) -> ((JibeDisutility) td).getGradientComponent(l));
        attributes.put("c_surf",(l,td,tt) -> ((JibeDisutility) td).getSurfaceComponent(l));
        attributes.put("c_attr",(l,td,tt) -> ((JibeDisutility) td).getAmbienceComponent(l));
        attributes.put("c_stress",(l,td,tt) -> ((JibeDisutility) td).getStressComponent(l));
        attributes.put("c_jct",(l,td,tt) -> ((JibeDisutility) td).getJunctionComponent(l));
        return attributes;
    }

    public static LinkedHashMap<String,TravelAttribute> getJibe4(String mode, Vehicle veh) {
        LinkedHashMap<String,TravelAttribute> attributes = new LinkedHashMap<>();
        attributes.put("gradient",(l,td,tt) -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("comfort",(l,td,tt) -> LinkComfort.getComfortFactor(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("vgvi",(l,td,tt) -> LinkAmbience.getVgviFactor(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("lighting",(l,td,tt) -> LinkAmbience.getLightingFactor(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("shannon", (l,td,tt) -> LinkAmbience.getShannonFactor(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("crime", (l,td,tt) -> LinkAmbience.getCrimeFactor(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("POIs",(l,td,tt) -> LinkAmbience.getPoiFactor(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("negPOIs",(l,td,tt) -> LinkAmbience.getNegativePoiFactor(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("ambience", (l,td,tt) -> LinkAmbience.getDayAmbience(l) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("stressLink",(l,td,tt) -> LinkStress.getStress(l,mode) * tt.getLinkTravelTime(l,0.,null,veh));
        attributes.put("stressJct",(l,td,tt) -> getJibe4StressJct(mode,l,tt,veh));
        return attributes;
    }

    private static double getJibe4StressJct(String mode, Link l, TravelTime tt, Vehicle veh) {
        if((boolean) l.getAttributes().getAttribute("crossVehicles")) {
            return JctStress.getStress(l,mode) * tt.getLinkTravelTime(l,0.,null,veh) *
                    (Math.min((double) l.getAttributes().getAttribute("crossWidth") / l.getLength(), 1.));
        } else return 0.;
    }


}
