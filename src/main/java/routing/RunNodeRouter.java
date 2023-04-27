package routing;

import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.disutility.DistanceDisutility;
import routing.disutility.JibeDisutility;
import routing.travelTime.WalkTravelTime;
import trads.RouteIndicatorCalculator;
import trads.io.TradsRouteWriter;
import trip.Place;
import trip.Trip;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static trip.Place.*;

// Router in which disutility functions remain constant for all trips
public class RunNodeRouter {

    private final static Logger log = Logger.getLogger(RunNodeRouter.class);
    private static final String SEP = ",";
    private static final String ORIGIN_NODE = "originNode";
    private static final String DESTINATION_NODE = "destinationNode";

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 4) {
            throw new RuntimeException("Program requires at least 4 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Input file path \n" +
                    "(2) Output file path (.gpkg) \n" +
                    "(3) Mode (walk or bike) \n");
        }

        Resources.initializeResources(args[0]);
        String inputODPairs = args[1];
        String outputFile = args[2];
        String mode = args[3];

        // Read network
        Network modeNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

        // Read OD nodes
        Set<Trip> trips = new LinkedHashSet<>();
        Counter counter = new Counter("Read " + " OD pairs.");
        BufferedReader in = new BufferedReader(new FileReader(inputODPairs));
        String recString = in.readLine();
        String[] header = recString.split(SEP);
        int posOrigin = findPositionInArray(ORIGIN_NODE,header);
        int posDestination = findPositionInArray(DESTINATION_NODE,header);

        while ((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] lineElements = recString.split(SEP);
            Id<Node> origin = Id.createNodeId(lineElements[posOrigin]);
            Id<Node> destination = Id.createNodeId(lineElements[posDestination]);

            Map<Place,Coord> coords = new HashMap<>(2);
            coords.put(ORIGIN,modeNetwork.getNodes().get(origin).getCoord());
            coords.put(DESTINATION,modeNetwork.getNodes().get(destination).getCoord());

            Map<Place,Boolean> coordsInBoundary = new HashMap<>(2);
            coordsInBoundary.put(ORIGIN,true);
            coordsInBoundary.put(DESTINATION,true);

            trips.add(new Trip("na",(int) counter.getCounter(),0,0,mode,null,null,null,coords,coordsInBoundary));
        }

        // CREATE VEHICLE & SET UP TRAVEL TIME
        Vehicle veh;
        TravelTime tt;
        if(mode.equals(TransportMode.walk)) {
            veh = null;
            tt = new WalkTravelTime();
        } else if (mode.equals(TransportMode.bike)) {
            Bicycle bicycle = new Bicycle(null);
            veh = bicycle.getVehicle();
            tt = bicycle.getTravelTime();
        } else {
            throw new RuntimeException("Routing not set up for mode " + mode);
        }

        // Route results
        RouteIndicatorCalculator calc = new RouteIndicatorCalculator(trips);
        calc.network(mode + "_short", ORIGIN, DESTINATION, veh, modeNetwork, modeNetwork, new DistanceDisutility(), tt, null, true);
        calc.network(mode + "_fast", ORIGIN, DESTINATION, veh, modeNetwork, modeNetwork, new OnlyTimeDependentTravelDisutility(tt), tt, null, true);
        calc.network(mode + "_jibe", ORIGIN, DESTINATION, veh, modeNetwork, modeNetwork, new JibeDisutility(mode,tt,0.,0.), tt, null, true);

        TradsRouteWriter.write(trips, outputFile, calc.getAllAttributeNames());


    }
    private static int findPositionInArray (String string, String[] array) {
        int ind = -1;
        for (int a = 0; a < array.length; a++) {
            if (array[a].equalsIgnoreCase(string)) {
                ind = a;
            }
        }
        if (ind == -1) {
            log.error ("Could not find element " + string +
                    " in array");
        }
        return ind;
    }
}
