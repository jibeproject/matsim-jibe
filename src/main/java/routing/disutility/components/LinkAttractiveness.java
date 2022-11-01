package routing.disutility.components;

import org.matsim.api.core.v01.network.Link;

public class LinkAttractiveness {

    // VGVI factor (for daytime attractiveness)
    public static double getVgviFactor (Link link){
        double vgvi = (double) link.getAttributes().getAttribute("vgvi");
        return 1. - vgvi;
    }

    // Lighting factor (for nighttime attractiveness)
    public static double getLightingFactor (Link link){
        int lights = (int) link.getAttributes().getAttribute("streetLights");
        return 1. - Math.min(1., 15 * lights / link.getLength());
    }

    public static double getAttractiveness(Link link, boolean night) {
        if(!night) return getVgviFactor(link);
        else return getLightingFactor(link);
    }

}
