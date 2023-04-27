package census;

import gis.ShpReader;
import io.ioUtils;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.Bicycle;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;
import trip.Trip;

import java.io.*;
import java.util.Map;
import java.util.Set;

import static trip.Place.DESTINATION;
import static trip.Place.HOME;

public class RunCensusVolumeRouter {

    private final static Logger logger = Logger.getLogger(RunCensusVolumeRouter.class);

    private final static char SEP = ',';

    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 4) {
            throw new RuntimeException("Program requires 4 arguments: \n" +
                    "(0) Properties file\n" +
                    "(1) MSOAs Shapefile\n" +
                    "(2) Census matrix\n" +
                    "(3) Output file path");
        }

        Resources.initializeResources(args[0]);

        String zonesFile = args[1];
        String censusMatrix = args[2];
        String outputCsv = args[3];

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Map<String, Geometry> MSOAs = ShpReader.readZones(zonesFile,"MSOA11CD");
        Set<Trip> trips = CensusReader.readAndProcessMatrix(MSOAs,censusMatrix,1.);

        // Get mode
        String mode = trips.iterator().next().getMainMode();
        logger.info("Identified mode " + mode + " from census data.");

        // Read network
        Network modeSpecificNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

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

        // CALCULATOR
        LinkVolumeCalculator calc = new LinkVolumeCalculator(trips);

        // Run short and fast routing (for reference)
        calc.calculate(mode + "_short", HOME, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new DistanceDisutility(), tt);
        calc.calculate(mode + "_fast", HOME, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new OnlyTimeDependentTravelDisutility(tt), tt);
        calc.calculate(mode + "_jibe", HOME, DESTINATION, veh, modeSpecificNetwork, modeSpecificNetwork, new JibeDisutility(mode,tt), tt);


        // Write results
        Map<String,int[]> allResults = calc.getAllResults();

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(outputCsv),false);
        assert out != null;

        StringBuilder header = new StringBuilder();
        header.append("link");
        for(String name : allResults.keySet()) {
            header.append(SEP).append(name);
        }
        out.println(header);

        for(Link link : modeSpecificNetwork.getLinks().values()) {
            StringBuilder line = new StringBuilder();
            line.append(link.getId().toString());
            for(int[] result : allResults.values()) {
                line.append(SEP).append(result[link.getId().index()]);
            }
            out.println(line);
        }
        out.close();
        logger.info("Printed " + allResults.size() + " volumes for " + modeSpecificNetwork.getLinks().size() + " links.");
    }

}
