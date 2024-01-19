package network;

import gis.GisUtils;
import io.ioUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.Bicycle;
import routing.Gradient;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import routing.travelTime.WalkTravelTime;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

// Code to determine the network links that are fully within each zone (for a given mode)

public class WriteLinksPerZone {
    private final static char SEP = ',';
    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 5) {
            throw new RuntimeException("Program requires 3 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Zones file (.gpkg) \n" +
                    "(2) ID attribute \n" +
                    "(3) Output file (.csv) \n" +
                    "(4) Mode");
        }

        Resources.initializeResources(args[0]);
        String inputFilename = args[1];
        String idAttribute = args[2];
        String outputCsv = args[3];
        String mode = args[4];

        // Read regions
        Set<SimpleFeature> features = GisUtils.readGpkg(inputFilename);

        // Read shapes
        Network network = NetworkUtils2.readModeSpecificNetwork(mode);

        // Get links per zone
        Map<SimpleFeature, IdSet<Link>> linksPerZone = GisUtils.calculateLinksIntersectingZones(features,network);

        // Travel time and vehicle
        TravelTime tt;
        Vehicle veh;

        if(mode.equals(TransportMode.bike)) {
            Bicycle bicycle = new Bicycle(null);
            tt = bicycle.getTravelTime();
            veh = bicycle.getVehicle();
        } else if (mode.equals(TransportMode.walk)) {
            tt = new WalkTravelTime();
            veh = null;
        } else throw new RuntimeException("Modes other than walk and bike are not supported!");

        // Create CSV
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv), false);
        assert out != null;

        // Write header
        out.println(idAttribute + SEP + "linkID" + SEP + "length" + SEP + "time" + SEP +
                "gradient" + SEP + "vgvi" + SEP + "stressLink" + SEP + "stressJct");

        // Write rows
        for (Map.Entry<SimpleFeature, IdSet<Link>> entry : linksPerZone.entrySet()) {
            String zoneId = (String) entry.getKey().getAttribute(idAttribute);
            for(Id<Link> linkId : entry.getValue()) {
                Link link = network.getLinks().get(linkId);
                double linkLength = link.getLength();
                double linkTime = tt.getLinkTravelTime(link,0.,null,veh);

                double stressJct = 0.;
                if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                    double junctionWidth = Math.min(linkLength,(double) link.getAttributes().getAttribute("crossWidth"));
                    stressJct = (junctionWidth / linkLength) * JctStress.getStress(link,mode);
                }

                out.println(zoneId + SEP + linkId.toString() + SEP + linkLength + SEP + linkTime + SEP +
                        Math.max(Math.min(Gradient.getGradient(link),0.5),0.) + SEP +
                        Math.max(0.,0.81 - LinkAmbience.getVgviFactor(link)) + SEP +
                        LinkStress.getStress(link,mode) + SEP +
                        stressJct);
            }
        }
        out.close();
    }
}
