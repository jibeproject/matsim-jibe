package routing;

import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import routing.disutility.JibeDisutility3;
import routing.travelTime.WalkTravelTime;
import io.TripRouteWriter;
import trip.Place;
import trip.Trip;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

// Similar to RunNodeRouter, but with different disutility function for different trips

public class RunNodeRouter2 {

    private final static Logger log = Logger.getLogger(RunNodeRouter2.class);
    private static final String SEP = ",";
    private static final String MC_AMBIENCE = "mcAmbience";
    private static final String MC_STRESS = "mcStress";
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

        // Read mode-specific network
        Network modeNetwork = NetworkUtils2.readModeSpecificNetwork(mode);

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

        // Shortest dijkstra algorithm
        TravelDisutility tdFast = new OnlyTimeDependentTravelDisutility(tt);
        LeastCostPathCalculator dijkstraFast = new FastDijkstraFactory(false).
                createPathCalculator(modeNetwork, tdFast, tt);

        // Read OD nodes
        Set<Trip> trips = new LinkedHashSet<>();
        Counter counter = new Counter("Read " + " OD pairs.");
        BufferedReader in = new BufferedReader(new FileReader(inputODPairs));
        String recString = in.readLine();
        String[] header = recString.split(SEP);
        int posMcAttr = findPositionInArray(MC_AMBIENCE,header);
        int posMcStress = findPositionInArray(MC_STRESS,header);
        int posOrigin = findPositionInArray(ORIGIN_NODE,header);
        int posDestination = findPositionInArray(DESTINATION_NODE,header);

        while ((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] lineElements = recString.split(SEP);

            double mcAmbience = Double.parseDouble(lineElements[posMcAttr]);
            double mcStress = Double.parseDouble(lineElements[posMcStress]);

            Id<Node> origin = Id.createNodeId(lineElements[posOrigin]);
            Id<Node> destination = Id.createNodeId(lineElements[posDestination]);

            Map<Place,Coord> coords = new HashMap<>(2);
            coords.put(ORIGIN,modeNetwork.getNodes().get(origin).getCoord());
            coords.put(DESTINATION,modeNetwork.getNodes().get(destination).getCoord());

            Map<Place,Boolean> coordsInBoundary = new HashMap<>(2);
            coordsInBoundary.put(ORIGIN,true);
            coordsInBoundary.put(DESTINATION,true);

            Trip trip = new Trip("na",(int) counter.getCounter(),0,0,mode,null,null,null,coords,coordsInBoundary);
            trips.add(trip);

            JibeDisutility3 tdJibe = new JibeDisutility3(mode,tt,true,mcAmbience,mcStress);
            LeastCostPathCalculator dijkstraJibe = new FastDijkstraFactory(false).
                    createPathCalculator(modeNetwork, tdJibe, tt);

            LeastCostPathCalculator.Path pathFast = dijkstraFast.calcLeastCostPath(modeNetwork.getNodes().get(origin), modeNetwork.getNodes().get(destination), 0., null, veh);
            LeastCostPathCalculator.Path pathJibe = dijkstraJibe.calcLeastCostPath(modeNetwork.getNodes().get(origin), modeNetwork.getNodes().get(destination), 0., null, veh);

            storeResults("fast",trip,pathFast);
            storeResults("jibe",trip,pathJibe);
        }

        TripRouteWriter.write(trips, modeNetwork, outputFile, false, Set.of("mc_ambience","mc_stress","cost","time","dist"));
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

    private static void storeResults(String route, Trip trip, LeastCostPathCalculator.Path path) {
        Map<String,Object> results = new LinkedHashMap<>();

        // Set cost and time
        results.put("cost",path.travelCost);
        results.put("time",path.travelTime);

        // Set distance
        double dist = path.links.stream().mapToDouble(Link::getLength).sum();
        results.put("dist",dist);

        // Set path
        List<Id<Link>> linkIDs = path.links.stream().map(Identifiable::getId).collect(Collectors.toList());
        trip.addRoute(route,linkIDs,dist);

        // Set attributes
        trip.setAttributes(route,results);

    }
}
