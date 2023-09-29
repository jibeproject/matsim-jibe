package trads;

import gis.GpkgReader;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.Bicycle;
import routing.disutility.JibeDisutility2;
import routing.travelTime.WalkTravelTime;
import trads.calculate.LogitDataCalculator;
import trads.io.TradsCsvWriter;
import trads.io.TradsReader;
import trip.Trip;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class RunMultiRouter {

    private final static Logger logger = Logger.getLogger(RunMultiRouter.class);

    // Parameters for MC Simulation
    public static void main(String[] args) throws IOException, FactoryException {
        if (args.length != 3) {
            throw new RuntimeException("Program requires 4 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Output csv file path\n" +
                    "(2) Mode");
        }

        Resources.initializeResources(args[0]);
        String outputCsv = args[1];
        String mode = args[2];

        // Read network
        Network network = NetworkUtils2.readModeSpecificNetwork(mode);

        // Read Boundary Shapefile
        logger.info("Reading boundary shapefile...");
        Geometry boundary = GpkgReader.readNetworkBoundary();

        // Read in TRADS trips from CSV
        logger.info("Reading person micro data from ascii file...");
        Set<Trip> trips = TradsReader.readTrips(boundary);

        // Filter to only routable trips with chosen mode
        Set<Trip> selectedTrips = trips.stream()
                .filter(t -> t.routable(ORIGIN, DESTINATION))
                .collect(Collectors.toSet());

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

        // Precalculate origin/destination nodes for each trip
        logger.info("Precalculating origin.destination nnoes");
        for(Trip trip : selectedTrips) {
            Node origNode = network.getNodes().get(NetworkUtils.getNearestLinkExactly(network, trip.getCoord(ORIGIN)).getToNode().getId());
            Node destNode = network.getNodes().get(NetworkUtils.getNearestLinkExactly(network, trip.getCoord(DESTINATION)).getToNode().getId());
            trip.setNodes(origNode,destNode);
        }

        // CALCULATOR
        LogitDataCalculator calc = new LogitDataCalculator(selectedTrips);

        // Test different combinations of values for gradient, vgvi/lighting, shannon, stress, jctStress (up to 10x distance each)
        for(int mcStressLink = 0 ; mcStressLink <= 0.05 ; mcStressLink += 0.0005) {
            JibeDisutility2 disutilty = new JibeDisutility2(mode,tt,0.067,0,mcStressLink,
                    0,0,0,0);
            calc.calculate(veh,network,disutilty,tt);
        }

        // Aggregate to double arrays todo: this path

    }
}
