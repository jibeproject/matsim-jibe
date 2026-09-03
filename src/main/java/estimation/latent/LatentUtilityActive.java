package estimation.latent;


import gis.GpkgReader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LatentUtilityActive implements LatentUtilityComponent {

    private static final GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();

    final LatentUtilityComponent latentBikeRouter;
    final LatentUtilityComponent latentWalkRouter;

    static Logger logger = Logger.getLogger(LatentUtilityActive.class);

    public LatentUtilityActive(LatentUtilityComponent latentBikeRouter, LatentUtilityComponent latentWalkRouter) {
        this.latentBikeRouter = latentBikeRouter;
        this.latentWalkRouter = latentWalkRouter;
    }

    @Override
    public void update(double[] x) {
        latentBikeRouter.update(x);
        latentWalkRouter.update(x);
    }

    @Override
    public String getStats() {
        return latentBikeRouter.getStats() + "\n" + latentWalkRouter.getStats();
    }

    public static LineString drawPath(List<Id<Link>> linkIDs, Network network, Map<Integer, SimpleFeature> networkFeatures) {

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

    public void mapRoutes(String outputGpkg, String[] ids, Network network) throws IOException {

        Map<Integer, SimpleFeature> networkFeatures = GpkgReader.readEdges();
        CoordinateReferenceSystem crs = networkFeatures.values().iterator().next().getFeatureType().getCoordinateReferenceSystem();

        final SimpleFeatureType TYPE = createRouteFeatureType(crs);
        final SimpleFeatureBuilder builder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("route",TYPE);

        // Get paths
        for(LatentRouter latentRouter : List.of((LatentRouter) latentBikeRouter,(LatentRouter) latentWalkRouter)) {
            LatentRouter.PathData paths = latentRouter.getPathData();
            for(int i = 0 ; i < paths.length ; i++) {
                if(paths.tooShort[i]) {
                    continue;
                }
                List<Id<Link>> links = paths.path[i].links.stream().map(Identifiable::getId).toList();
                builder.add(drawPath(links, network, networkFeatures));
                builder.add(ids[i]);
                builder.add(latentRouter.mode);
                builder.add(paths.time[i]);
                collection.add(builder.buildFeature(null));
            }
        }

        // Write Geopackage
        File outputFile = new File(outputGpkg);
        if(outputFile.delete()) {
            logger.warn("File " + outputFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputFile);
        out.init();
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription("routes");
        out.add(entry,collection);
        out.createSpatialIndex(entry);
        out.close();
    }

    private static SimpleFeatureType createRouteFeatureType(CoordinateReferenceSystem crs) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Routes");
        builder.setCRS(crs);
        builder.add("Path", LineString.class);
        builder.length(20).add("id",String.class);
        builder.length(10).add("mode",String.class);
        builder.add("time",Double.class);
        return builder.buildFeatureType();
    }


}
