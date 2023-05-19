package trads;

import com.google.common.collect.Iterables;
import gis.GpkgReader;
import io.ioUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkStress;
import trads.calculate.MultiLinkCalculator;
import trads.io.TradsReader;
import trip.Trip;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class RunTradsLinkDetour {

    private final static double DECAY_PARAMETER = -9.2;

    private final static Logger logger = Logger.getLogger(RunTradsLinkDetour.class);
    private final static char SEP = ',';

    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output csv file path \n" +
                    "(2) Mode");
        }

        Resources.initializeResources(args[0]);
        String outputCsv = args[1];
        String mode = args[2];

        String boundaryFilePath = Resources.instance.getString(Properties.NETWORK_BOUNDARY);

        // Read network
        Network modeNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readBoundary(boundaryFilePath);

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary).stream()
                .filter(t -> (t.getEndPurpose().isMandatory() && t.getStartPurpose().equals(TradsPurpose.HOME)) ||
                        (t.getStartPurpose().isMandatory() && t.getEndPurpose().equals(TradsPurpose.HOME)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        logger.info("Calculating for " + trips.size() + " trips.");


//        // Travel time and vehicle
//        TravelTime tt;
//        Vehicle veh;
//
//        if (mode.equals(TransportMode.bike)) {
//            Bicycle bicycle = new Bicycle(null);
//            tt = bicycle.getTravelTime();
//            veh = bicycle.getVehicle();
//        } else if (mode.equals(TransportMode.walk)) {
//            tt = new WalkTravelTime();
//            veh = null;
//        } else throw new RuntimeException("Modes other than walk and bike are not supported!");

        // Write header
        writeHeader(outputCsv);

        // Calculate shortest, fastest, and jibe route
        for(List<Trip> partition : Iterables.partition(trips,1000)) {
            Map<Trip, IdMap<Link,Double>> results = MultiLinkCalculator.calculate(partition,ORIGIN, DESTINATION, modeNetwork, modeNetwork, 1.5);
            writeResults(results,outputCsv,mode,modeNetwork);
        }
    }

    private static void writeHeader(String outputCsv) {
        // WriteLinksToCsv
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv), false);
        assert out != null;

        // Write header
        String header = "IDNumber" +
                SEP + "PersonNumber" +
                SEP + "TripNumber" +
                SEP + "linkID" +
                SEP + "detour" +
                SEP + "vgvi" +
                SEP + "shannon" +
                SEP + "POIs" +
                SEP + "negPOIs" +
                SEP + "crime" +
                SEP + "linkStress" +
                SEP + "jctStress";
        out.println(header);
        out.close();
    }


    private static void writeResults(Map<Trip, IdMap<Link,Double>> results, String outputCsv, String mode, Network net) {
        // Write results to combined CSV
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv), true);
        assert out != null;

        // Write rows
        for (Map.Entry<Trip, IdMap<Link,Double>> result : results.entrySet()) {

            for (Map.Entry<Id<Link>, Double> linkDetour : result.getValue().entrySet()) {
                Id<Link> linkId = linkDetour.getKey();
                Link link = net.getLinks().get(linkId);
                String row = result.getKey().getHouseholdId() +
                        SEP + result.getKey().getPersonId() +
                        SEP + result.getKey().getTripId() +
                        SEP + linkId +
                        SEP + linkDetour.getValue() +
                        SEP + link.getAttributes().getAttribute("vgvi") +
                        SEP + link.getAttributes().getAttribute("shannon") +
                        SEP + link.getAttributes().getAttribute("POIs") +
                        SEP + link.getAttributes().getAttribute("negPOIs") +
                        SEP + link.getAttributes().getAttribute("crime") +
                        SEP + LinkStress.getStress(link,mode) +
                        SEP + JctStress.getStress(link,mode);
                out.println(row);
            }
        }
        out.close();
    }

/*
    private static void writeAggregateResultsToCsv(Set<Trip> trips, String outputCsv, String mode, Network net) {

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv), false);
        assert out != null;

        String header = "IDNumber" +
                SEP + "PersonNumber" +
                SEP + "TripNumber" +
                SEP + "vgvi" +
                SEP + "shannon" +
                SEP + "POIs" +
                SEP + "negPOIs" +
                SEP + "crime" +
                SEP + "linkStress" +
                SEP + "jctStress";
        out.println(header);

        for (Trip trip : trips) {
            if(trip.getLinks() != null) {
                double sumWt = 0.;
                double vgvi = 0;
                double shannon = 0;
                double POIs = 0;
                double negPOIs = 0;
                double crime = 0;
                double stressLink = 0;
                double stressJct = 0;
                for (Map.Entry<Id<Link>, Double> linkDetour : trip.getLinks().entrySet()) {
                    Id<Link> linkId = linkDetour.getKey();
                    Link link = net.getLinks().get(linkId);
                    double wt = link.getLength() * Math.exp((linkDetour.getValue() - 1) * DECAY_PARAMETER);
                    sumWt += wt;
                    vgvi += wt * (double) link.getAttributes().getAttribute("vgvi");
                    shannon += wt * (double) link.getAttributes().getAttribute("shannon");
                    POIs += wt * (double) link.getAttributes().getAttribute("POIs");
                    negPOIs += wt * (double) link.getAttributes().getAttribute("negPOIs");
                    crime += wt * (double) link.getAttributes().getAttribute("crime");
                    stressLink += wt * LinkStress.getStress(link, mode);
                    stressJct += wt * JctStress.getStress(link, mode);
                }
                String row = trip.getHouseholdId() +
                        SEP + trip.getPersonId() +
                        SEP + trip.getTripId() +
                        SEP + vgvi / sumWt +
                        SEP + shannon / sumWt +
                        SEP + POIs / sumWt +
                        SEP + negPOIs / sumWt +
                        SEP + crime / sumWt +
                        SEP + stressLink / sumWt +
                        SEP + stressJct / sumWt;
                out.println(row);
            }
        }
        out.close();
    }
*/



}
