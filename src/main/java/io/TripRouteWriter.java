package io;

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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;
import trip.Route;
import trip.Trip;

import java.io.File;
import java.io.IOException;
import java.util.*;

import trip.Place;
import static trip.Place.ORIGIN;
import static trip.Place.DESTINATION;

public class TripRouteWriter {

    private final static Logger logger = Logger.getLogger(TripRouteWriter.class);
    private static final GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();

    // Write geometries to .gpkg
    public static void write(Set<Trip> trips, Network network, String outputGpkg, boolean unique, Map<String, List<String>> attributes) throws FactoryException, IOException {
        final Set<String> allAttributes = new LinkedHashSet<>();
        for(Map.Entry<String,List<String>> e : attributes.entrySet()) {
            allAttributes.addAll(e.getValue());
        }
        write(trips,network,outputGpkg,unique,allAttributes);
    }

    public static void write(Set<Trip> trips, Network network, String outputGpkg, boolean unique, Set<String> allAttributes) throws FactoryException, IOException {

        final SimpleFeatureType routeTYPE = createRouteFeatureType(unique,allAttributes);
        final SimpleFeatureType nodeTYPE = createNodeFeatureType();

        final SimpleFeatureBuilder routeFeatureBuilder = new SimpleFeatureBuilder(routeTYPE);
        final SimpleFeatureBuilder nodeFeatureBuilder = new SimpleFeatureBuilder(nodeTYPE);

        final DefaultFeatureCollection routeCollection = new DefaultFeatureCollection("Routes",routeTYPE);
        final DefaultFeatureCollection nodeCollection = new DefaultFeatureCollection("Nodes",nodeTYPE);

        // Read in edges file
        Map<Integer, SimpleFeature> networkFeatures = GpkgReader.readEdges();
        int tripCounter = 0;
        int pathCounter = 0;

        for(Trip trip : trips) {

            // Increment counter
            tripCounter++;

            // todo: adapt to visualise routes between other locations (e.g., HOME & MAIN). Only relevant for activity-based modelling.
            if (trip.routable(ORIGIN, DESTINATION)) {

                // Origin / destination coordinates
                nodeCollection.add(createNodeFeature(nodeFeatureBuilder,trip,ORIGIN));
                nodeCollection.add(createNodeFeature(nodeFeatureBuilder,trip,DESTINATION));

                // Path
                if(unique) {
                    int i = 0;
                    for(Route route : trip.getUniqueRoutes()) {
                        LineString path = drawPath(route.getLinkIds(), network, networkFeatures);
                        routeCollection.add(createRouteFeature(routeFeatureBuilder,trip,route,i,null,path,true));
                        i++;
                        pathCounter++;
                    }
                } else {
                    for (Map.Entry<String, Route> e : trip.getAllRoutes().entrySet()) {
                        Route route = e.getValue();
                        if (!route.getLinkIds().isEmpty()) {
                            LineString path = drawPath(route.getLinkIds(), network, networkFeatures);
                            routeCollection.add(createRouteFeature(routeFeatureBuilder,trip,route,e.getKey(),allAttributes,path,false));
                        }
                        pathCounter++;
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

    private static SimpleFeatureType createRouteFeatureType(boolean unique, Set<String> attributes) throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Routes");
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));

        // add attributes in order
        builder.add("Path", LineString.class);
        builder.add(Resources.instance.getString(Properties.HOUSEHOLD_ID),String.class);
        builder.add(Resources.instance.getString(Properties.PERSON_ID),Integer.class);
        builder.add(Resources.instance.getString(Properties.TRIP_ID),Integer.class);
        builder.add("MainMode",String.class);
        if(unique) {
            builder.add("RouteId", Integer.class);
        } else {
            builder.length(20).add("Route", String.class);
        }
        builder.add("distance",Double.class);
        if(!unique) {
            for(String attribute : attributes) {
                builder.add(attribute, Double.class);
            }
        }
        return builder.buildFeatureType();
    }

    private static SimpleFeature createRouteFeature(SimpleFeatureBuilder builder, Trip trip, Route route,
                                                    Object routeName, Set<String> allAttributes, LineString path, boolean unique) {
        builder.add(path);
        builder.add(trip.getHouseholdId());
        builder.add(trip.getPersonId());
        builder.add(trip.getTripId());
        builder.add(trip.getMainMode());
        builder.add(routeName);
        builder.add(route.getDistance());
        if(!unique) {
            for (String attribute : allAttributes) {
                builder.add(trip.getAttribute((String) routeName, attribute));
            }
        }
        return builder.buildFeature(null);
    }

    private static SimpleFeatureType createNodeFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Nodes");
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));

        // add attributes in order
        builder.add("Node", Point.class);
        builder.add(Resources.instance.getString(Properties.HOUSEHOLD_ID),String.class);
        builder.add(Resources.instance.getString(Properties.PERSON_ID),Integer.class);
        builder.add(Resources.instance.getString(Properties.TRIP_ID),Integer.class);
        builder.add("Place", String.class);

        // build the type
        return builder.buildFeatureType();
    }

    private static SimpleFeature createNodeFeature(SimpleFeatureBuilder builder, Trip trip, Place place) {
        Coord coord = trip.getCoord(place);
        Point point = gf.createPoint(new Coordinate(coord.getX(), coord.getY()));
        builder.add(point);
        builder.add(trip.getHouseholdId());
        builder.add(trip.getPersonId());
        builder.add(trip.getTripId());
        builder.add(place.toString());
        return builder.buildFeature(null);
    }

    private static LineString drawPath(List<Id<Link>> linkIDs, Network network, Map<Integer, SimpleFeature> networkFeatures) {

        Coordinate[] path = new Coordinate[]{};
        Map<Id<Link>, ? extends Link> networkLinks = network.getLinks();
        Coord startNode = networkLinks.get(linkIDs.get(0)).getFromNode().getCoord();
        Coordinate refCoord = new Coordinate(startNode.getX(),startNode.getY());

        for (Id<Link> linkId : linkIDs) {
            int edgeId = (int) networkLinks.get(linkId).getAttributes().getAttribute("edgeID");
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
