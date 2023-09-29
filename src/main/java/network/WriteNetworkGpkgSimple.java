package network;

import com.google.common.math.LongMath;
import gis.GpkgReader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

// Writes network with links in both directions (useful for visualisations where out/return details are different)

public class WriteNetworkGpkgSimple {

    private final static Logger log = Logger.getLogger(WriteNetworkGpkgSimple.class);

    public static void main(String[] args) throws FactoryException, IOException {

        if(args.length < 2 || args.length > 3) {
            throw new RuntimeException("Program requires 2 or 3 arguments:\n" +
                    "(0) Properties file (.properties)" +
                    "(1) Output edges (.gpkg)\n" +
                    "(2) OPTIONAL: mode (for printing a mode-specific network)");
        }

        Resources.initializeResources(args[0]);

        String outputEdgesFilePath = args[1];

        String modeFilter = null;
        if(args.length == 3) {
            modeFilter = args[2];
        }

        // Read MATSim network
        log.info("Reading MATSim network...");
        Network network = NetworkUtils2.readFullNetwork();

        // Filter network to a specific mode (if applicable)
        if(modeFilter != null) {
            Network modeSpecificNetwork = NetworkUtils.createNetwork();
            new TransportModeNetworkFilter(network).filter(modeSpecificNetwork, Collections.singleton(modeFilter));
            network = modeSpecificNetwork;
        }

        write(network,outputEdgesFilePath);
    }

    private static SimpleFeatureType createFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("links");
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));

        // add attributes in order
        builder.add("path", LineString.class);
        builder.add("linkID",String.class);
        builder.add("length",Double.class);
        builder.add("freespeed",Double.class);
        builder.add("lanes",Double.class);
        builder.add("capacity",Double.class);
        builder.add("car",Boolean.class);
        builder.add("bike",Boolean.class);
        builder.add("walk",Boolean.class);
        builder.add("disconnected_car",Boolean.class);
        builder.add("disconnected_bike",Boolean.class);
        builder.add("disconnected_walk",Boolean.class);
        builder.add("motorway",Boolean.class);
        builder.add("trunk",Boolean.class);
        builder.add("primary",Boolean.class);
        builder.add("urban",Boolean.class);

        return builder.buildFeatureType();
    }

    public static void write(Network network, String outputEdgesFilePath) throws IOException, FactoryException {

        // Read edges
        Map<Integer,SimpleFeature> edges = GpkgReader.readEdges();

        // Prepare geopackage data
        final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        final SimpleFeatureType TYPE = createFeatureType();
        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("Routes",TYPE);

        // Write directed MATSim network as gpkg
        int counter = 0;
        int forwardLinks = 0;
        int backwardLinks = 0;
        for (Link link : network.getLinks().values()) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                log.info("Processing link " + counter + " / " + network.getLinks().size());
            }
            int edgeID = (int) link.getAttributes().getAttribute("edgeID");

            // EdgeID of -1 indicates an artificial edge (e.g., connector at edge of network)
            if (edgeID != -1) {
                boolean fwd = (boolean) link.getAttributes().getAttribute("fwd");
                Coord fromNode = link.getFromNode().getCoord();
                Coord toNode = link.getToNode().getCoord();
                SimpleFeature edge = edges.get(edgeID);
                Coordinate[] coords = new Coordinate[0];
                try {
                    coords = ((LineString) edge.getDefaultGeometry()).getCoordinates().clone();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Check direction matches from/to node and reverse if necessary
                Coordinate fromCoord = coords[0];
                Coordinate toCoord = coords[coords.length - 1];
                Coordinate fromNodeCoord = new Coordinate(fromNode.getX(), fromNode.getY());
                Coordinate toNodeCoord = new Coordinate(toNode.getX(), toNode.getY());
                if ((fwd && fromCoord.equals2D(fromNodeCoord) && toCoord.equals2D(toNodeCoord)) ||
                        (!fwd && fromCoord.equals2D(toNodeCoord) && toCoord.equals2D(fromNodeCoord))) {
                    forwardLinks++;
                } else if ((fwd && fromCoord.equals2D(toNodeCoord) && toCoord.equals2D(fromNodeCoord)) ||
                        (!fwd && fromCoord.equals2D(fromNodeCoord) && toCoord.equals2D(toNodeCoord))) {
                    backwardLinks++;
                    ArrayUtils.reverse(coords);
                } else {
                    throw new RuntimeException("Edge " + edgeID + " doesn't match its from and to nodes!");
                }

                double length = link.getLength();


                // Reverse if not in forward direction
                if (!fwd) {
                    ArrayUtils.reverse(coords);
                }

                // Geometry
                featureBuilder.add(geometryFactory.createLineString(coords));

                // Other attributes
                featureBuilder.add(link.getId().toString());
                featureBuilder.add(length);
                featureBuilder.add(link.getFreespeed());
                featureBuilder.add(link.getNumberOfLanes());
                featureBuilder.add(link.getCapacity());
                featureBuilder.add(link.getAllowedModes().contains(TransportMode.car));
                featureBuilder.add(link.getAllowedModes().contains(TransportMode.bike));
                featureBuilder.add(link.getAllowedModes().contains(TransportMode.walk));
                featureBuilder.add(link.getAttributes().getAttribute("disconnected_" + TransportMode.car));
                featureBuilder.add(link.getAttributes().getAttribute("disconnected_" + TransportMode.bike));
                featureBuilder.add(link.getAttributes().getAttribute("disconnected_" + TransportMode.walk));
                featureBuilder.add(link.getAttributes().getAttribute("motorway"));
                featureBuilder.add(link.getAttributes().getAttribute("trunk"));
                featureBuilder.add(link.getAttributes().getAttribute("primary"));
                featureBuilder.add(link.getAttributes().getAttribute("urban"));
                SimpleFeature feature = featureBuilder.buildFeature(null);
                collection.add(feature);
            }
        }

        log.info(forwardLinks + " edges in the correct direction");
        log.info(backwardLinks + " edges in wrong direction needed to be reversed");

        // Write Geopackage
        File outputEdgesFile = new File(outputEdgesFilePath);
        if(outputEdgesFile.delete()) {
            log.warn("File " + outputEdgesFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputEdgesFile);
        out.init();
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription("network");
        out.add(entry,collection);
        out.createSpatialIndex(entry);
        out.close();
    }

}
