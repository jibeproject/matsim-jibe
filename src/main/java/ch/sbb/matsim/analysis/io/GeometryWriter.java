/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.io;

import ch.sbb.matsim.analysis.data.GeometryData;
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
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Node;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Writes geometry data to geopackage
 */
public final class GeometryWriter {

    private final static Logger log = Logger.getLogger(GeometryWriter.class);
    private final static String SHORTEST_DISTANCE_ROUTE_NAME = "shortestDistance";
    private final static String LEAST_TIME_ROUTE_NAME = "fastest";


    public static <T> void writeGpkg(GeometryData<T> geometryData, Map<String, Node> zoneNodeMap,
                                     String inputEdgesGpkg, String outputGpkg) throws IOException, FactoryException {

        HashMap<String, GeometryData> multiGeometryData = new HashMap<>();
        multiGeometryData.put("NA",geometryData);
        writeGpkg(multiGeometryData,zoneNodeMap,inputEdgesGpkg,outputGpkg);
    }

    public static <T> void writeGpkg(HashMap<String, GeometryData> multiGeometryData, Map<String, Node> zoneNodeMap,
                                     String inputEdgesGpkg, String outputGpkg) throws FactoryException, IOException {

        final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        final Set<String> attributes = multiGeometryData.entrySet().iterator().next().getValue().attributeMatrices.keySet();

        final SimpleFeatureType routeTYPE = createRouteFeatureType(attributes);
        final SimpleFeatureType nodeTYPE = createNodeFeatureType();

        final SimpleFeatureBuilder routeFeatureBuilder = new SimpleFeatureBuilder(routeTYPE);
        final SimpleFeatureBuilder nodeFeatureBuilder = new SimpleFeatureBuilder(nodeTYPE);

        final DefaultFeatureCollection routeCollection = new DefaultFeatureCollection("Routes",routeTYPE);
        final DefaultFeatureCollection nodeCollection = new DefaultFeatureCollection("Nodes",nodeTYPE);

        // Build origin/destination nodes
        for (Map.Entry<String, Node> entry : zoneNodeMap.entrySet()) {
            Point point = buildPoint(entry.getValue().getCoord(), geometryFactory);
            nodeFeatureBuilder.add(point);
            nodeFeatureBuilder.add(entry.getKey());
            SimpleFeature feature = nodeFeatureBuilder.buildFeature(null);
            nodeCollection.add(feature);
        }

        // If there is a "shortest distance route", sort detour info
        GeometryData shortestDistanceData = multiGeometryData.get(SHORTEST_DISTANCE_ROUTE_NAME);
        GeometryData leastTimeData = multiGeometryData.get(LEAST_TIME_ROUTE_NAME);

        // Read in edges file (put back in loop if necessary...)
        Map<Integer, SimpleFeature> networkFeatures = GpkgReader.readEdges(new File(inputEdgesGpkg));

        // Build routes
        for (Map.Entry<String,GeometryData> entry : multiGeometryData.entrySet()) {

            log.info("Writing geometries for route " + entry.getKey());

            GeometryData<T> geometryData = entry.getValue();

            // Prep
            T[] fromZoneIds = getSortedIds(geometryData.orig2index);
            T[] toZoneIds = getSortedIds(geometryData.dest2index);

            // Loop through zone IDs
            int counter = 0;
            for (T fromZoneId : fromZoneIds) {
                Coord startCoord = zoneNodeMap.get(fromZoneId).getCoord();
                counter++;
                if (LongMath.isPowerOfTwo(counter)) {
                    log.info("Processing zone " + counter + " / " + fromZoneIds.length);
                }
                for (T toZoneId : toZoneIds) {
                    int[] edges;
                    if(geometryData.linksTravelled != null) {
                        edges = (int[]) geometryData.linksTravelled.get(fromZoneId,toZoneId);
                    } else {
                        edges = new int[]{};
                    }
                    if(edges.length > 0) {
                        LineString path = buildPath(startCoord,edges,geometryFactory,networkFeatures);
                        double distanceM = geometryData.distanceMatrix.get(fromZoneId,toZoneId);
                        double timeS = geometryData.travelTimeMatrix.get(fromZoneId,toZoneId);
                        Double distanceDetour = null;
                        if(shortestDistanceData != null) {
                            double shortestDistanceM = shortestDistanceData.distanceMatrix.get(fromZoneId,toZoneId);
                            distanceDetour = distanceM / shortestDistanceM;
                        }
                        Double timeDetour = null;
                        if(leastTimeData != null) {
                            double leastTimeS = leastTimeData.travelTimeMatrix.get(fromZoneId,toZoneId);
                            timeDetour = timeS / leastTimeS;
                        }

                        routeFeatureBuilder.add(path);
                        routeFeatureBuilder.add(entry.getKey());
                        routeFeatureBuilder.add(fromZoneId.toString());
                        routeFeatureBuilder.add(toZoneId.toString());
                        routeFeatureBuilder.add(geometryData.costMatrix.get(fromZoneId,toZoneId));
                        routeFeatureBuilder.add(edges.length);
                        routeFeatureBuilder.add(distanceM);
                        routeFeatureBuilder.add(timeS);
                        routeFeatureBuilder.add(3.6 * distanceM / timeS);
                        routeFeatureBuilder.add(distanceDetour);
                        routeFeatureBuilder.add(timeDetour);
                        for(String attribute : attributes) {
                            double attr = geometryData.attributeMatrices.get(attribute).get(fromZoneId,toZoneId);
                            if(!attribute.startsWith("c_")) {
                                attr /= distanceM;
                            }
                            routeFeatureBuilder.add(attr);
                        }
                        SimpleFeature feature = routeFeatureBuilder.buildFeature(null);
                        routeCollection.add(feature);
                    }
                }
            }
        }

        // Write Geopackage
        File outputFile = new File(outputGpkg);
        if(outputFile.delete()) {
            log.warn("File " + outputFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputFile);
        out.init();
        FeatureEntry routeEntry = new FeatureEntry();
        FeatureEntry nodeEntry = new FeatureEntry();
        routeEntry.setDescription("routes");
        nodeEntry.setDescription("nodes");
        out.add(routeEntry,routeCollection);
        out.add(nodeEntry,nodeCollection);
        out.createSpatialIndex(routeEntry);
        out.createSpatialIndex(nodeEntry);
        out.close();

    }

    private static SimpleFeatureType createRouteFeatureType(Set<String> attributes) throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Routes");
        builder.setCRS(CRS.decode("EPSG:27700")); // <- Coordinate reference system

        // add attributes in order
        builder.add("Path", LineString.class);
        builder.length(20).add("Route", String.class);
        builder.length(8).add("From", String.class);
        builder.length(8).add("To", String.class);
        builder.add("cost", Double.class);
        builder.add("links",Double.class);
        builder.add("distance_m", Double.class);
        builder.add("tt_s", Double.class);
        builder.add("avgSpeed_kph", Double.class);
        builder.add("distance_detour",Double.class);
        builder.add("time_detour",Double.class);
        for(String attribute : attributes) {
            builder.add(attribute, Double.class);
        }


        // build the type
        final SimpleFeatureType TYPE = builder.buildFeatureType();

        return TYPE;
    }

    private static SimpleFeatureType createNodeFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Nodes");
        builder.setCRS(CRS.decode("EPSG:27700")); // <- Coordinate reference system

        // add attributes in order
        builder.add("Path", Point.class);
        builder.length(8).add("Zone", String.class);

        // build the type
        final SimpleFeatureType TYPE = builder.buildFeatureType();

        return TYPE;
    }

    public static Point buildPoint(Coord node, GeometryFactory geometryFactory) {
        return geometryFactory.createPoint(new Coordinate(node.getX(),node.getY()));
    }

    public static LineString buildPath(Coord startNode, int[] edgeIDs, GeometryFactory geometryFactory, Map<Integer, SimpleFeature> networkFeatures) {

        Coordinate[] path = new Coordinate[]{};
        Coordinate refCoord = new Coordinate(startNode.getX(),startNode.getY());

        for (int edgeId : edgeIDs) {
            SimpleFeature edge = networkFeatures.get(edgeId);
            Coordinate[] coords = new Coordinate[0];
            try {
                coords = ((LineString) edge.getDefaultGeometry()).getCoordinates().clone();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Coordinate firstCoord = coords[0];
            Coordinate lastCoord = coords[coords.length - 1];

            // Check if link is forward or reversed
            if (refCoord.equals2D(firstCoord)) {
                refCoord = lastCoord;
            } else {
                if (!refCoord.equals2D(lastCoord)) {
                    throw new RuntimeException("Edge " + edgeId + " does not line up with previous edge");
                }
                ArrayUtils.reverse(coords);
                refCoord = firstCoord;
            }

            path = ArrayUtils.addAll(path, coords);
        }

        return geometryFactory.createLineString(path);
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
