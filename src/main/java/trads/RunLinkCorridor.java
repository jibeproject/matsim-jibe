package trads;

import com.google.common.collect.Iterables;
import gis.GpkgReader;
import io.ioUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import routing.Bicycle;
import routing.disutility.DistanceDisutility;
import routing.travelTime.WalkTravelTime;
import trads.calculate.LinkCorridorCalculator;
import trads.io.TradsReader;
import trip.Purpose;
import trip.Trip;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static trip.Place.*;

// Code to calculate route-based corridors between origin-destination pairs.
// NOTE: Feasible for detour factors up to 50%

public class RunLinkCorridor {

    private final static Logger logger = Logger.getLogger(RunLinkCorridor.class);
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
        Set<Trip> trips = TradsReader.readTrips(boundary);//.stream()
//                .filter(t -> !((t.getEndPurpose().isMandatory() && t.getStartPurpose().equals(Purpose.HOME)) ||
//                        (t.getStartPurpose().isMandatory() && t.getEndPurpose().equals(Purpose.HOME))))
//                .collect(Collectors.toCollection(LinkedHashSet::new));
        logger.info("Calculating for " + trips.size() + " trips.");


        // Travel time and vehicle
        TravelTime tt;
        Vehicle veh;

        if (mode.equals(TransportMode.bike)) {
            Bicycle bicycle = new Bicycle(null);
            tt = bicycle.getTravelTime();
            veh = bicycle.getVehicle();
        } else if (mode.equals(TransportMode.walk)) {
            tt = new WalkTravelTime();
            veh = null;
        } else throw new RuntimeException("Modes other than walk and bike are not supported!");

        // Write header
        writeHeader(outputCsv);

        // Use time disutility
        TravelDisutility td = new DistanceDisutility();

        // Calculate shortest, fastest, and jibe route
        for(List<Trip> partition : Iterables.partition(trips,1000)) {
            Map<Trip, IdMap<Link,Double>> results = LinkCorridorCalculator.calculate(partition,HOME, DESTINATION, td, veh, modeNetwork, modeNetwork, 1.5);
            writeResults(results,outputCsv);
        }
    }

    private static void writeHeader(String outputCsv) {
        // WriteLinksToCsv
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv), false);
        assert out != null;

        // Write header
        String header = "IDNumber" + SEP + "PersonNumber" + SEP + "TripNumber" + SEP + "linkID" + SEP + "detour";
        out.println(header);
        out.close();
    }


    private static void writeResults(Map<Trip, IdMap<Link,Double>> results, String outputCsv) {
        // Write results to combined CSV
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv), true);
        assert out != null;

        // Write rows
        for (Map.Entry<Trip, IdMap<Link,Double>> result : results.entrySet()) {

            for (Map.Entry<Id<Link>, Double> linkDetour : result.getValue().entrySet()) {
                Id<Link> linkId = linkDetour.getKey();
                String row = result.getKey().getHouseholdId() +
                        SEP + result.getKey().getPersonId() +
                        SEP + result.getKey().getTripId() +
                        SEP + linkId +
                        SEP + (linkDetour.getValue() - 1.);
                out.println(row);
            }
        }
        out.close();
    }
}
