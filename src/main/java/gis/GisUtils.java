package gis;

import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
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

    public static Set<SimpleFeature> readGpkg(String gpkgFile) throws IOException {
        GeoPackage geopkg = new GeoPackage(new File(gpkgFile));
        SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);
        Set<SimpleFeature> features = new HashSet<>();
        while(r.hasNext()) {
            features.add(r.next());
        }
        r.close();
        geopkg.close();
        return Collections.unmodifiableSet(features);
    }

    public static void writeFeaturesToGpkg(SimpleFeatureCollection collection, String description, String outputFilePath) throws IOException {
        log.info("Writing features...");
        File outputFile = new File(outputFilePath);
        if(outputFile.delete()) {
            log.warn("File " + outputFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputFile);
        out.init();
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription(description);
        out.add(entry,collection);
        out.createSpatialIndex(entry);
        out.close();
    }

    public static Map<SimpleFeature, IdSet<Node>> assignNodesToZones(Collection<SimpleFeature> zones, Set<Id<Node>> nodeIds, Network network) {
        log.info("Assigning nodeIds to polygon features...");
        SpatialIndex zonesQt = createQuadtree(zones);
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
        return Collections.unmodifiableMap(nodesPerZone);
    }

    public static IdMap<Node,String> getCandidateNodes(Geometry region, Set<SimpleFeature> zones, Network network) {

        Set<Id<Node>> candidates = NetworkUtils2.getNodesInBoundary(network,region);
        IdMap<Node,String> results = new IdMap<>(Node.class);
        Map<SimpleFeature,Integer> nodesInside = new HashMap<>();

        log.info("Assigning nodeIds to polygon features...");
        SpatialIndex zonesQt = createQuadtree(zones);

        Counter counter = new Counter("Processing node "," / " + candidates.size());
        for (Id<Node> nodeId : candidates) {
            counter.incCounter();
            SimpleFeature z = findZone(network.getNodes().get(nodeId).getCoord(),zonesQt);
            if (z != null) {
                nodesInside.put(z,nodesInside.getOrDefault(z,0) + 1);
                results.put(nodeId,z.getID());
            }
        }
        log.info("Identified " + results.size() + " candidates within zones.");

        for(SimpleFeature z : zones) {
            if(!nodesInside.containsKey(z)) {
                Point centroid = ((Geometry) z.getDefaultGeometry()).getCentroid();
                results.put(NetworkUtils.getNearestNode(network,new Coord(centroid.getX(),centroid.getY())).getId(),z.getID());
                nodesInside.put(z,1);
            }
        }
        log.info("Identified " + results.size() + " candidates total.");

        return results;
    }

    public static Map<SimpleFeature, IdSet<Link>> calculateLinksIntersectingZones(Collection<SimpleFeature> zones, Network network) {
        Map<Integer,SimpleFeature> edges = GpkgReader.readEdges();
        log.info("Assigning linkIds to polygon features...");
        SpatialIndex zonesQt = createQuadtree(new HashSet<>(zones));
        Map<SimpleFeature, IdSet<Link>> linksPerZone = new HashMap<>(zones.size());
        Counter counter = new Counter("Processing link "," / " + network.getLinks().values().size());

        for (Link link : network.getLinks().values()) {
            SimpleFeature edge = edges.get((int) link.getAttributes().getAttribute("edgeID"));
            LineString line = (LineString) edge.getDefaultGeometry();
            List elements = zonesQt.query(line.getEnvelopeInternal());
            for(Object o : elements) {
                SimpleFeature z = (SimpleFeature) o;
                if(((Geometry) z.getDefaultGeometry()).intersects(line)) {
                    linksPerZone.computeIfAbsent(z, k -> new IdSet<>(Link.class)).add(link.getId());
                }
            }
            counter.incCounter();
        }
        return Collections.unmodifiableMap(linksPerZone);
    }

    private static SpatialIndex createQuadtree(Collection<SimpleFeature> features) {
        log.info("Creating spatial index");
        SpatialIndex zonesQt = new Quadtree();
        Counter counter = new Counter("Indexing zone "," / " + features.size());
        for (SimpleFeature feature : features) {
            counter.incCounter();
            Geometry geom = (Geometry) (feature.getDefaultGeometry());
            if(!geom.isEmpty()) {
                Envelope envelope = ((Geometry) (feature.getDefaultGeometry())).getEnvelopeInternal();
                zonesQt.insert(envelope, feature);
            } else {
                throw new RuntimeException("Null geometry for zone " + feature.getID());
            }
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

    public static Coord drawRandomPointFromGeometry(Geometry g) {
        Random rnd = MatsimRandom.getLocalInstance();
        Point p;
        double x, y;
        do {
            x = g.getEnvelopeInternal().getMinX()
                    + rnd.nextDouble() * (g.getEnvelopeInternal().getMaxX() - g.getEnvelopeInternal().getMinX());
            y = g.getEnvelopeInternal().getMinY()
                    + rnd.nextDouble() * (g.getEnvelopeInternal().getMaxY() - g.getEnvelopeInternal().getMinY());
            p = MGC.xy2Point(x, y);
        } while (!g.contains(p));
        return new Coord(p.getX(), p.getY());
    }

}
