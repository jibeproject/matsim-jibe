package gis;

import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GisUtils {

    private final static Logger log = Logger.getLogger(GisUtils.class);

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

    public static Map<SimpleFeature, IdSet<Link>> calculateLinksIntersectingZones(Collection<SimpleFeature> zones, Network network) {
        Map<Integer,SimpleFeature> edges = GpkgReader.readEdges();
        log.info("Assigning linkIds to polygon features...");
        SpatialIndex zonesQt = createQuadtree(new HashSet<>(zones));
        Map<SimpleFeature, IdSet<Link>> linksPerZone = new HashMap<>(zones.size());
        Counter counter = new Counter("Processing link "," / " + network.getLinks().size());

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

}
