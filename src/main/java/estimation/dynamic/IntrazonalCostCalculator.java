package estimation.dynamic;

import estimation.utilities.AbstractUtilityFunction;
import gis.GisUtils;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.opengis.feature.simple.SimpleFeature;
import routing.Gradient;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import trip.Place;
import trip.Trip;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IntrazonalCostCalculator {

    private static final Logger logger = Logger.getLogger(IntrazonalCostCalculator.class);
    private final int[] intrazonalTripIndices;
    private final double[] cost;
    private final double[] time;
    private final double[] gradient;
    private final double[] vgvi;
    private final double[] stressLink;
    private final double[] stressJct;

    public IntrazonalCostCalculator(Trip[] trips, AbstractUtilityFunction u, Set<SimpleFeature> zones, Network net, String mode) {

        // Fix average speed (m/s)
        double averageSpeed;
        if(mode.equals("bike")) {
            averageSpeed = 5.1;
        } else if(mode.equals("walk")) {
            averageSpeed = 1.38;
        } else {
            throw new RuntimeException("Mode " + mode + " not supported!");
        }

        // Get and index intrazonal trips
        int totalTripCount = trips.length;
        intrazonalTripIndices = new int[totalTripCount];
        Arrays.fill(intrazonalTripIndices,-1);
        int intrazonalTripCount = 0;
        for(int i = 0 ; i < trips.length ; i++) {
            Trip trip = trips[i];
            if(trip.getZone(Place.ORIGIN).equals(trip.getZone(Place.DESTINATION))) {
                intrazonalTripIndices[i] = intrazonalTripCount;
                intrazonalTripCount++;
            }
        }
        logger.info("Found " + intrazonalTripCount + " intrazonal " + mode + " trips.");

        // Determine set of links for each zone
        Map<SimpleFeature,IdSet<Link>> linksPerFeature = GisUtils.calculateLinksIntersectingZones(zones,net);
        Map<String,IdSet<Link>> linksPerZone = linksPerFeature.entrySet().stream().collect(Collectors.toMap(e -> ((String) e.getKey().getAttribute("geo_code")), Map.Entry::getValue));

        // Compute values for intrazonal trips
        cost = new double[intrazonalTripCount];
        time = new double[intrazonalTripCount];
        gradient = new double[intrazonalTripCount];
        vgvi = new double[intrazonalTripCount];
        stressLink = new double[intrazonalTripCount];
        stressJct = new double[intrazonalTripCount];

        for(int i = 0 ; i < trips.length ; i++) {
            int idx = intrazonalTripIndices[i];
            if(idx > -1) {
                double tripLength = u.value(i,"dist");
                if(tripLength == 0) {
                    logger.warn("Zero trip distance for household " + trips[i].getHouseholdId() + " person " + trips[i].getPersonId() +
                            " trip " + trips[i].getTripId() + "! Setting to 50m");
                    tripLength = 50;
                }
                double totLength = 0;
                double totGrad = 0;
                double totVgvi = 0;
                double totStressLink = 0;
                double totStressJct = 0;
                for(Id<Link> linkId : linksPerZone.get(trips[i].getZone(Place.ORIGIN))) {
                    Link link = net.getLinks().get(linkId);
                    double linkLength = link.getLength();
                    totLength += linkLength;
                    totGrad += linkLength * Math.max(Math.min(Gradient.getGradient(link),0.5),0.);
                    totVgvi += linkLength * Math.max(0.,0.81 - LinkAmbience.getVgviFactor(link));
                    totStressLink += linkLength * LinkStress.getStress(link,mode);
                    if((boolean) link.getAttributes().getAttribute("crossVehicles")) {
                        double junctionWidth = Math.min(link.getLength(),(double) link.getAttributes().getAttribute("crossWidth"));
                        totStressJct += junctionWidth * JctStress.getStress(link,mode);
                    }
                }
                double tripTime = tripLength / averageSpeed;
                double adj = tripTime * (tripLength / totLength);
                if(adj > tripTime) {
                    logger.warn("Trip distance for household " + trips[i].getHouseholdId() + " person " + trips[i].getPersonId() + " trip " + trips[i].getTripId() +
                            " (" + tripLength + "m) exceeds total length of all links intersecting zone " + trips[i].getZone(Place.ORIGIN) + " (" + totLength +
                            "m) . Setting to total length.");
                    adj = tripTime;
                }
                time[idx] = tripTime;
                gradient[idx] = totGrad * adj;
                vgvi[idx] = totVgvi * adj;
                stressLink[idx] = totStressLink * adj;
                stressJct[idx] = totStressJct * adj;
            }
        }
    }

    public boolean isIntrazonal(int tripIdx) {
        return intrazonalTripIndices[tripIdx] > -1;
    }

    public void updateCosts(double gammaGradient, double gammaVgvi, double gammaStressLink, double gammaStressJct) {
        for(int i = 0 ; i < time.length ; i++) {
            cost[i] = time[i] + gradient[i] * gammaGradient + vgvi[i] * gammaVgvi + stressLink[i] * gammaStressLink + stressJct[i] * gammaStressJct;
        }
    }

    double getCost(int tripIdx) {
        return cost[intrazonalTripIndices[tripIdx]];
    }

    double getTime(int tripIdx) {
        return time[intrazonalTripIndices[tripIdx]];
    }

    double getGradient(int tripIdx) {
        return gradient[intrazonalTripIndices[tripIdx]];
    }

    double getVgvi(int tripIdx) {
        return vgvi[intrazonalTripIndices[tripIdx]];
    }

    double getStressLink(int tripIdx) {
        return stressLink[intrazonalTripIndices[tripIdx]];
    }

    double getStressJct(int tripIdx) {
        return stressJct[intrazonalTripIndices[tripIdx]];
    }
    }
