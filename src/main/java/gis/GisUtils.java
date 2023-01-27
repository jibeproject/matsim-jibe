package gis;

import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GisUtils {

    private final static Logger log = Logger.getLogger(GisUtils.class);
    private final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public static Point nodeToPoint(Node node) {
        Coord coord = node.getCoord();
        return GEOMETRY_FACTORY.createPoint(new Coordinate(coord.getX(),coord.getY()));
    }

    public static void writeFeaturesToGpkg(SimpleFeatureCollection collection, String outputFilePath) throws IOException {
        log.info("Writing polygons...");
        File outputFile = new File(outputFilePath);
        if(outputFile.delete()) {
            log.warn("File " + outputFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputFile);
        out.init();
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription("grid");
        out.add(entry,collection);
        out.createSpatialIndex(entry);
        out.close();
    }

    public static Map<SimpleFeature, IdSet<Node>> assignNodesToZones(Set<SimpleFeature> zones, Set<Id<Node>> nodeIds, Network network) {
        log.info("Assigning nodeIds to polygon features...");
        SpatialIndex zonesQt = createZoneQuadtree(zones);
        Map<SimpleFeature, IdSet<Node>> nodesPerZone = new HashMap<>(zones.size());
        Counter counter = new Counter("Processing node "," / " + nodeIds.size());
        for (Id<Node> nodeId : nodeIds) {
            counter.incCounter();
            SimpleFeature z = findZone(network.getNodes().get(nodeId).getCoord(),zonesQt);
            if (z != null) {
                nodesPerZone.computeIfAbsent(z, k -> new IdSet<>(Node.class)).add(nodeId);
            } else {
                log.warn("No polygon contains nodeId " + nodeId.toString());
            }
        }

        return nodesPerZone;
    }

    private static SpatialIndex createZoneQuadtree(Set<SimpleFeature> zones) {
        log.info("Creating spatial index");
        SpatialIndex zonesQt = new Quadtree();
        Counter counter = new Counter("Indexing zone "," / " + zones.size());
        for (SimpleFeature zone : zones) {
            counter.incCounter();
            Envelope envelope = ((Geometry) (zone.getDefaultGeometry())).getEnvelopeInternal();
            zonesQt.insert(envelope, zone);
        }
        return zonesQt;
    }

    private static SimpleFeature findZone(Coord coord, SpatialIndex zonesQt) {
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(coord.getX(), coord.getY()));
        List elements = zonesQt.query(pt.getEnvelopeInternal());
        for (Object o : elements) {
            SimpleFeature z = (SimpleFeature) o;
            if (((Geometry) z.getDefaultGeometry()).intersects(pt)) {
                return z;
            }
        }
        return null;
    }

}
