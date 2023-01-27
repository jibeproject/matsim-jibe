package gis;

import java.util.Map;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.grid.GridElement;
import org.geotools.grid.GridFeatureBuilder;
import org.geotools.grid.PolygonElement;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeatureType;

public class IntersectionGridBuilder extends GridFeatureBuilder {
    final GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
    final Geometry boundary;

    final Counter counter = new Counter("Created "," cells.");
    int id = 0;

    public IntersectionGridBuilder(SimpleFeatureType type, Geometry geom) {
        super(type);
        this.boundary = geom;
    }

    public void setAttributes(GridElement el, Map<String, Object> attributes) {
        counter.incCounter();
        attributes.put("id", ++id);
        Coordinate centre = ((PolygonElement) el).getCenter();
        attributes.put("centroid_x",centre.x);
        attributes.put("centroid_y",centre.y);
    }

    @Override
    public boolean getCreateFeature(GridElement el) {
        Coordinate c = ((PolygonElement) el).getCenter();
        Geometry p = gf.createPoint(c);
        return boundary.contains(p);
    }
}