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
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import trip.Route;
import trip.Trip;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static trads.io.TradsAttributes.*;
import static trip.Place.DESTINATION;
import static trip.Place.ORIGIN;

public class TradsUniqueRouteWriter {

    private static final Logger logger = Logger.getLogger(TradsUniqueRouteWriter.class);
    private static final GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();

    // Write geometries to .gpkg
    public static void write(Set<Trip> trips, String inputEdgesGpkg, String outputGpkg) throws FactoryException, IOException {

        final SimpleFeatureType routeTYPE = createRouteFeatureType();
        final SimpleFeatureType nodeTYPE = createNodeFeatureType();

        final SimpleFeatureBuilder routeFeatureBuilder = new SimpleFeatureBuilder(routeTYPE);
        final SimpleFeatureBuilder nodeFeatureBuilder = new SimpleFeatureBuilder(nodeTYPE);

        final DefaultFeatureCollection routeCollection = new DefaultFeatureCollection("Routes",routeTYPE);
        final DefaultFeatureCollection nodeCollection = new DefaultFeatureCollection("Nodes",nodeTYPE);

        // Read in edges file
        Map<Integer, SimpleFeature> networkFeatures = GpkgReader.readEdges(new File(inputEdgesGpkg));
        int tripCounter = 0;
        int pathCounter = 0;

        for(Trip trip : trips) {

            int tripId = trip.getTripId();

            // Increment counter
            tripCounter++;

            // Get origin/destination
            Coord origCoord = trip.getCoord(ORIGIN);
            Coord destCoord = trip.getCoord(DESTINATION);

            // Origin coordinate
            Point origPoint = gf.createPoint(new Coordinate(origCoord.getX(),origCoord.getY()));
            nodeFeatureBuilder.add(origPoint);
            nodeFeatureBuilder.add(true);
            nodeFeatureBuilder.add(tripId);
            SimpleFeature origFeature = nodeFeatureBuilder.buildFeature(null);
            nodeCollection.add(origFeature);

            // Destination coordinate
            Point destPoint = gf.createPoint(new Coordinate(destCoord.getX(),destCoord.getY()));
            nodeFeatureBuilder.add(destPoint);
            nodeFeatureBuilder.add(false);
            nodeFeatureBuilder.add(tripId);
            SimpleFeature destFeature = nodeFeatureBuilder.buildFeature(null);
            nodeCollection.add(destFeature);

            // Path
            List<Route> routePaths = trip.getUniqueRoutes();
            for (int i = 0 ; i < routePaths.size() ; i++) {
                Route route = routePaths.get(i);
                int[] links = route.getLinks();
                if(links.length > 0) {
                    pathCounter++;
                    LineString line = buildPath(route.getStartCoord(),links,networkFeatures);
                    routeFeatureBuilder.add(line);
                    routeFeatureBuilder.add(trip.getHouseholdId());
                    routeFeatureBuilder.add(trip.getPersonId());
                    routeFeatureBuilder.add(tripId);
                    routeFeatureBuilder.add(trip.getZone(ORIGIN));
                    routeFeatureBuilder.add(trip.getZone(DESTINATION));
                    routeFeatureBuilder.add(i);
                    routeFeatureBuilder.add(route.getDistance());
                    routeFeatureBuilder.add(route.getTime());
                    SimpleFeature feature = routeFeatureBuilder.buildFeature(null);
                    routeCollection.add(feature);
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

    private static SimpleFeatureType createRouteFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Routes");
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM))); // <- Coordinate reference system

        // add attributes in order
        builder.add("Path", LineString.class);
        builder.add(HOUSEHOLD_ID,String.class);
        builder.add(PERSON_ID,Integer.class);
        builder.add(TRIP_ID,Integer.class);
        builder.add("origin",String.class);
        builder.add("destination",String.class);
        builder.add("RouteId", Integer.class);
        builder.add("distance",Double.class);
        builder.add("time",Double.class);

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

    public static LineString buildPath(Coord startNode, int[] edgeIDs, Map<Integer, SimpleFeature> networkFeatures) {

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

        return gf.createLineString(path);
    }

}
