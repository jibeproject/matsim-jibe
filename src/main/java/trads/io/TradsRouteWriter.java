package trads.io;

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
import org.matsim.api.core.v01.network.Link;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import routing.graph.TreeNode;
import trip.Trip;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class TradsRouteWriter {

    private final static Logger logger = Logger.getLogger(TradsRouteWriter.class);

    // Write geometries to .gpkg
    public static void write(Set<Trip> trips, String outputGpkg, Map<String, List<String>> attributes) throws FactoryException, IOException {
        final Set<String> allAttributes = new LinkedHashSet<>();
        for(Map.Entry<String,List<String>> e : attributes.entrySet()) {
            allAttributes.addAll(e.getValue());
        }
        write(trips,outputGpkg,allAttributes);
    }

    public static void write(Set<Trip> trips, String outputGpkg, Set<String> allAttributes) throws FactoryException, IOException {

        final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        final SimpleFeatureType routeTYPE = createRouteFeatureType(allAttributes);
        final SimpleFeatureType nodeTYPE = createNodeFeatureType();

        final SimpleFeatureBuilder routeFeatureBuilder = new SimpleFeatureBuilder(routeTYPE);
        final SimpleFeatureBuilder nodeFeatureBuilder = new SimpleFeatureBuilder(nodeTYPE);

        final DefaultFeatureCollection routeCollection = new DefaultFeatureCollection("Routes",routeTYPE);
        final DefaultFeatureCollection nodeCollection = new DefaultFeatureCollection("Nodes",nodeTYPE);

        // Read in edges file
        String inputEdgesGpkg = Resources.instance.getString(Properties.NETWORK_LINKS);
        Map<Integer, SimpleFeature> networkFeatures = GpkgReader.readEdges(new File(inputEdgesGpkg));
        int tripCounter = 0;
        int pathCounter = 0;

        for(Trip trip : trips) {

            // Increment counter
            tripCounter++;

            if (trip.routable(ORIGIN, DESTINATION)) {

                // Get origin/destination
                Coord origCoord = trip.getCoord(ORIGIN);
                Coord destCoord = trip.getCoord(DESTINATION);

                // Origin coordinate
                Point origPoint = geometryFactory.createPoint(new Coordinate(origCoord.getX(), origCoord.getY()));
                nodeFeatureBuilder.add(origPoint);
                nodeFeatureBuilder.add(true);
                nodeFeatureBuilder.add(trip.getTripId());
                SimpleFeature origFeature = nodeFeatureBuilder.buildFeature(null);
                nodeCollection.add(origFeature);

                // Destination coordinate
                Point destPoint = geometryFactory.createPoint(new Coordinate(destCoord.getX(), destCoord.getY()));
                nodeFeatureBuilder.add(destPoint);
                nodeFeatureBuilder.add(false);
                nodeFeatureBuilder.add(trip.getTripId());
                SimpleFeature destFeature = nodeFeatureBuilder.buildFeature(null);
                nodeCollection.add(destFeature);

                // Path
                if (!trip.getUniqueRoutes().isEmpty()) {
                    for (Map.Entry<String, int[]> e : trip.getAllRoutePaths().entrySet()) {
                        pathCounter++;
                        String route = e.getKey();
                        if (e.getValue().length > 0) {
                            LineString path = drawFromEdgeIDs(trip.getStartCoord(route), e.getValue(), geometryFactory, networkFeatures);
                            routeFeatureBuilder.add(path);
                            routeFeatureBuilder.add(route);
                            for (String attribute : allAttributes) {
                                routeFeatureBuilder.add(trip.getAttribute(route, attribute));
                            }
                            SimpleFeature feature = routeFeatureBuilder.buildFeature(null);
                            routeCollection.add(feature);
                        }
                    }
                } else {
                    Set<TreeNode> paths = trip.getPaths();
                    for (TreeNode p : paths) {
                        pathCounter++;
                        String name = String.valueOf(pathCounter);
                        LineString path = drawFromPathNode(p, geometryFactory, networkFeatures);
                        routeFeatureBuilder.add(path);
                        routeFeatureBuilder.add(name);
                        for (String attribute : allAttributes) {
                            routeFeatureBuilder.add(trip.getAttribute(name, attribute));
                        }
                        SimpleFeature feature = routeFeatureBuilder.buildFeature(null);
                        routeCollection.add(feature);
                    }
                }
            }
        }
        logger.info("Writing " + tripCounter + " trips with " + pathCounter + " paths...");

        // Write Geopackage
        File outputFile = new File(outputGpkg);
        if(outputFile.delete()) {
            logger.warn("File " + outputFile.getAbsolutePath() + " already exists. Overwriting.");
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
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM))); // <- Coordinate reference system

        // add attributes in order
        builder.add("Path", LineString.class);
        builder.length(20).add("Route", String.class);
        for(String attribute : attributes) {
            builder.add(attribute, Double.class);
        }

        // build the type
        return builder.buildFeatureType();
    }

    private static SimpleFeatureType createNodeFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Nodes");
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));

        // add attributes in order
        builder.add("Path", Point.class);
        builder.add("Origin", Boolean.class);
        builder.add("TripID", Integer.class);

        // build the type
        return builder.buildFeatureType();
    }

    public static LineString drawFromEdgeIDs(Coord startNode, int[] edgeIDs, GeometryFactory geometryFactory, Map<Integer, SimpleFeature> networkFeatures) {

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

    public static LineString drawFromPathNode(TreeNode n, GeometryFactory geometryFactory, Map<Integer, SimpleFeature> networkFeatures) {

        Coord endNodeCoord = n.link.getToNode().getCoord();

        Coordinate[] path = new Coordinate[]{};
        Coordinate refCoord = new Coordinate(endNodeCoord.getX(),endNodeCoord.getY());

        TreeNode curr = n;
        while(curr.link != null) {
            Link link = curr.link;
            int edgeId = (int) link.getAttributes().getAttribute("edgeID");
            SimpleFeature edge = networkFeatures.get(edgeId);
            Coordinate[] coords = new Coordinate[0];
            try {
                coords = ((LineString) edge.getDefaultGeometry()).getCoordinates().clone();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Coordinate firstCoord = coords[0];
            Coordinate lastCoord = coords[coords.length - 1];

            if(refCoord.equals2D(lastCoord)) {
                refCoord = firstCoord;
            } else {
                if(!refCoord.equals2D(firstCoord)) {
                    throw new RuntimeException("Edge " + edgeId + " does not line up with previous edge");
                }
                ArrayUtils.reverse(coords);
                refCoord = lastCoord;
            }

            path = ArrayUtils.addAll(coords,path);
            curr = curr.parent;
        }

        return geometryFactory.createLineString(path);
    }

}
