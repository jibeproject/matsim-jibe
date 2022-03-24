/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.skims;

import com.google.common.math.LongMath;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureReader;
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
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.io.IOUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper methods to write and read matrices as CSV files (well, actually semi-colon separated files).
 *
 * @author mrieser / SBB
 */
public final class GeometryIO {

    private final static Logger log = Logger.getLogger(GeometryIO.class);

    private final static String SEP = ";";
    private final static String NL = "\n";

    public static <T> void writeAsCSV(GeometryData<T> geometryData, Network network,String filenamePrefix) throws IOException {
        log.info("Writing Path Geometries as Edges");
        try (BufferedWriter writer = IOUtils.getBufferedWriter(filenamePrefix + "_edges.csv.gz")) {
            writeEdges(geometryData, network, writer);
        }
//        log.info("Writing Path Geometries as Node Indices");
//        try (BufferedWriter writer = IOUtils.getBufferedWriter(filenamePrefix + "_nodes.csv.gz")) {
//            writeNodes(geometryData, network, writer);
//        }
//        log.info("Writing Path Geometries as X/Y Coordinates");
//        try (BufferedWriter writer = IOUtils.getBufferedWriter(filenamePrefix + "_coords.csv.gz")) {
//            writeCoords(geometryData, network, writer);
//        }
    }

    private static<T> void writeEdges(GeometryData<T> geometryData, Network network, BufferedWriter writer) throws IOException {
        writer.write("FROM" + SEP + "TO" + SEP + "EDGES_TRAVELLED");
        writer.write(NL);

        T[] fromZoneIds = getSortedIds(geometryData.orig2index);
        T[] toZoneIds = getSortedIds(geometryData.dest2index);

        Map<Integer,Integer> linkIndexMap = new HashMap<>();
        for(Link link : network.getLinks().values()) {
            linkIndexMap.put(link.getId().index(),(int) link.getAttributes().getAttribute("edgeID"));
        }

        int counter = 0;
        for (T fromZoneId : fromZoneIds) {
            counter++;
            if(LongMath.isPowerOfTwo(counter)) {
                log.info("Writing zone " + counter + " / " + fromZoneIds.length);
            }
            for (T toZoneId : toZoneIds) {
                writer.write(fromZoneId.toString());
                writer.append(SEP);
                writer.write(toZoneId.toString());
                writer.append(SEP);
                writer.write(indicesToEdges((int[]) geometryData.linksTravelled.get(fromZoneId,toZoneId),linkIndexMap));
                writer.append(NL);
            }
        }
        writer.flush();
    }

    private static <T> void writeNodes(GeometryData<T> geometryData, Network network, BufferedWriter writer) throws IOException {
        writer.write("FROM" + SEP + "TO" + SEP + "NODES_PASSED");
        writer.write(NL);

        T[] fromZoneIds = getSortedIds(geometryData.orig2index);
        T[] toZoneIds = getSortedIds(geometryData.dest2index);

        Map<Integer,String> nodeIndexMap = new HashMap<>();
        for(Node node : network.getNodes().values()) {
            nodeIndexMap.put(node.getId().index(),node.getId().toString());
        }

        int counter = 0;
        for (T fromZoneId : fromZoneIds) {
            counter++;
            if(LongMath.isPowerOfTwo(counter)) {
                log.info("Writing zone " + counter + " / " + fromZoneIds.length);
            }
            for (T toZoneId : toZoneIds) {
                writer.write(fromZoneId.toString());
                writer.append(SEP);
                writer.write(toZoneId.toString());
                writer.append(SEP);
                writer.write(indicesToNodes((int[]) geometryData.nodeGeometries.get(fromZoneId,toZoneId),nodeIndexMap));
                writer.append(NL);
            }
        }
        writer.flush();
    }

    private static <T> void writeCoords(GeometryData<T> geometryData, Network network, BufferedWriter writer) throws IOException {
        Map<Integer, Coord> nodeIndexCoordMap = new HashMap<>();;
        for(Node node : network.getNodes().values()) {
            nodeIndexCoordMap.put(node.getId().index(), node.getCoord());
        }

        writer.write("FROM" + SEP + "TO" + SEP + "NODES PASSED");
        writer.write(NL);

        T[] fromZoneIds = getSortedIds(geometryData.orig2index);
        T[] toZoneIds = getSortedIds(geometryData.dest2index);

        int counter = 0;
        for (T fromZoneId : fromZoneIds) {
            counter++;
            if(LongMath.isPowerOfTwo(counter)) {
                log.info("Writing zone " + counter + " / " + fromZoneIds.length);
            }
            for (T toZoneId : toZoneIds) {
                writer.write(fromZoneId.toString());
                writer.append(SEP);
                writer.write(toZoneId.toString());
                writer.append(SEP);
                writer.write(indicesToCoords((int[]) geometryData.nodeGeometries.get(fromZoneId,toZoneId), nodeIndexCoordMap));
                writer.append(NL);
            }
        }
        writer.flush();
    }

    public static <T> void writeGpkg(GeometryData<T> geometryData, Network network, Map<String, Node> zoneNodeMap) throws IOException, FactoryException {

        // Prep
        T[] fromZoneIds = getSortedIds(geometryData.orig2index);
        T[] toZoneIds = getSortedIds(geometryData.dest2index);

        // Create link index map
        Map<Integer,Integer> linkIndexMap = new HashMap<>();
        for(Link link : network.getLinks().values()) {
            linkIndexMap.put(link.getId().index(),(int) link.getAttributes().getAttribute("edgeID"));
        }

        // Read in edges file
        Map<Integer, SimpleFeature> networkFeatures = new HashMap<>(530000);
        File edgesFile = new File("/Users/corinstaves/Documents/manchester/JIBE/network/network_v2.9.gpkg");
        GeoPackage geopkg = new GeoPackage(edgesFile);
        SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);
        while(r.hasNext()) {
            SimpleFeature f = r.next();
            networkFeatures.put((int) f.getAttribute("edgeID"),f);
        }
        r.close();
        geopkg.close();

        // Prepare geopackage data
        final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        final SimpleFeatureType TYPE = createFeatureType();
        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("Routes",TYPE);

        // Loop through zone IDs
        int counter = 0;
        for (T fromZoneId : fromZoneIds) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                log.info("Processing zone " + counter + " / " + fromZoneIds.length);
            }
            int startNode = Integer.parseInt(zoneNodeMap.get(fromZoneId).getId().toString());
            for (T toZoneId : toZoneIds) {
                int[] edges = (int[]) geometryData.nodeGeometries.get(fromZoneId,toZoneId);
                LineString path = buildPath(startNode,edges,linkIndexMap,geometryFactory,networkFeatures);
                featureBuilder.add(path);
                featureBuilder.add(fromZoneId.toString());
                featureBuilder.add(toZoneId.toString());
                SimpleFeature feature = featureBuilder.buildFeature(null);
                collection.add(feature);
            }
        }

        GeoPackage out = new GeoPackage(File.createTempFile("geopkg", ".gpkg", new File("/Users/corinstaves/Documents/manchester/JIBE/")));
        out.init();

        // Write Geopackage
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription("test");
        out.add(entry,collection);
        out.createSpatialIndex(entry);
        out.close();

    }

    private static SimpleFeatureType createFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Location");
        builder.setCRS(CRS.decode("EPSG:27700")); // <- Coordinate reference system

        // add attributes in order
        builder.add("Path", LineString.class);
        builder.length(8).add("From", String.class);
        builder.length(8).add("To",String.class);

        // build the type
        final SimpleFeatureType TYPE = builder.buildFeatureType();

        return TYPE;
    }

    private static LineString buildPath(int startNode, int[] edgeIndeces, Map<Integer,Integer> linkIndexMap, GeometryFactory geometryFactory, Map<Integer, SimpleFeature> networkFeatures) {

        Coordinate[] path = new Coordinate[]{};

        int nextNode = startNode;
        for (int i = 0 ; i < edgeIndeces.length ; i++) {
            int edgeId = linkIndexMap.get(edgeIndeces[i]);
            SimpleFeature edge = networkFeatures.get(edgeId);
            int fromNode = (int) edge.getAttribute("from");
            int toNode = (int) edge.getAttribute("to");
            Coordinate[] coords = ((LineString) edge.getDefaultGeometry()).getCoordinates();

            // Check if link is forward or reversed
            boolean fwd = nextNode == fromNode;
            if(fwd) {
                nextNode = toNode;
            } else {
                assert nextNode == toNode;
                nextNode = fromNode;
                ArrayUtils.reverse(coords);
            }

            path = ArrayUtils.addAll(path,coords);
        }

        return geometryFactory.createLineString(path);
    }

    private static String indicesToEdges(int[] linkIndices, Map<Integer,Integer> linkIndexMap) {
        StringBuilder stp = new StringBuilder();
        for(int index : linkIndices) {
            stp.append(",");
            stp.append(linkIndexMap.get(index));
        }
        stp.replace(0,1,"[");
        stp.append("]");
        return stp.toString();
    }

    private static String indicesToNodes(int[] nodeIndices, Map<Integer,String> nodeMap) {
        StringBuilder stp = new StringBuilder();
        for(int index : nodeIndices) {
            stp.append(",");
            stp.append(nodeMap.get(index));
        }
        stp.replace(0,1,"[");
        stp.append("]");
        return stp.toString();
    }

    private static <T> String indicesToCoords(int[] nodeIndices, Map<Integer,Coord> coordMap) {
        StringBuilder stp = new StringBuilder();
        for(int index : nodeIndices) {
            Coord coord = coordMap.get(index);
            stp.append(",");
            stp.append(coord.getX());
            stp.append(",");
            stp.append(coord.getY());
        }
        stp.replace(0,1,"[");
        stp.append("]");
        return stp.toString();
    }

    private static <T> T[] getSortedIds(Map<T, Integer> id2index) {
        // the array-creation is only safe as long as the generated array is only within this class!
        @SuppressWarnings("unchecked")
        T[] ids = (T[]) (new Object[id2index.size()]);
        for (Map.Entry<T, Integer> e : id2index.entrySet()) {
            ids[e.getValue()] = e.getKey();
        }
        return ids;
    }

    @FunctionalInterface
    public interface IdConverter<T> {
        T parse(String id);
    }
}
