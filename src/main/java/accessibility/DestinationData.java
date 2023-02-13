package accessibility;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DestinationData {
    private final static Logger log = Logger.getLogger(DestinationData.class);
    private final static GeometryFactory gf = new GeometryFactory();
    private final static String SEP = ",";
    private final static String ID_VAR = "ID";
    private final static String X_VAR = "X";
    private final static String Y_VAR = "Y";
    private final static String WEIGHT_VAR = "WEIGHT";

    private final Map<String, List<Coord>> coords = new LinkedHashMap<>();
    private final Map<String, Double> weights = new LinkedHashMap<>();

    public DestinationData(String filename, Geometry destinationBoundary) throws IOException {
        String recString;

        int lines = 0;
        int destinationsOutsideBoundary = 0;
        Counter counter = new Counter("Read "," destinations.");

        BufferedReader in = IOUtils.getBufferedReader(filename);
        recString = in.readLine();
        String[] header = recString.split(SEP);

        int posId = findPositionInArray(ID_VAR,header);
        int posX = findPositionInArray(X_VAR,header);
        int posY = findPositionInArray(Y_VAR,header);
        int posWt = findPositionInArray(WEIGHT_VAR,header);
        if(posWt == -1) {
            log.warn("No weight attribute found in " + filename + ". Setting all destination weights to 1.");
        }

        while((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] lineElements = recString.split(SEP);

            String id = lineElements[posId];
            double x = Double.parseDouble(lineElements[posX]);
            double y = Double.parseDouble(lineElements[posY]);

            if(destinationBoundary.contains(gf.createPoint(new Coordinate(x,y)))) {
                double wt = posWt == -1 ? 1. : Double.parseDouble(lineElements[posWt]);
                if(coords.containsKey(id)) {
                    if(weights.get(id) == wt) {
                        coords.get(id).add(new Coord(x,y));
                    } else {
                        throw new RuntimeException("Mismatching weights for destination " + id);
                    }
                } else {
                    List<Coord> coords = new ArrayList<>();
                    coords.add(new Coord(x,y));
                    this.coords.put(id, coords);
                    weights.put(id, wt);
                }
            } else {
                destinationsOutsideBoundary++;
            }
        }
        log.info("Read " + lines + " lines.");
        log.info("Loaded " + coords.size() + " unique destinations and " +
                coords.values().stream().mapToInt(List::size).sum() + " access points.");
        log.info(destinationsOutsideBoundary + " destinations ignored because their coordinates were outside the boundary.");
    }

    public Map<String, List<Coord>> getCoords() {
        return coords;
    }

    public Map<String, Double> getWeights() {
        return weights;
    }

    public Map<String, List<Node>> getNodes(Network xy2lNetwork, Network routingNetwork) {
        Map<String, List<Node>> idNodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<Coord>> e : coords.entrySet()) {
            List<Node> nodes = new ArrayList<>();
            for (Coord coord : e.getValue()) {
                nodes.add(routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, coord).getToNode().getId()));
            }
            idNodeMap.put(e.getKey(), nodes);
        }
        return idNodeMap;
    }

    private static int findPositionInArray (String string, String[] array) {
        int ind = -1;
        for (int a = 0; a < array.length; a++) {
            if (array[a].equalsIgnoreCase(string)) {
                ind = a;
            }
        }
        return ind;
    }

}
