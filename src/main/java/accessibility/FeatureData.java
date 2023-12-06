package accessibility;

import org.apache.log4j.Logger;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.IOException;
import java.util.List;

// Tools for reading zone system for accessibility analysis

public class FeatureData {

    private final static Logger log = Logger.getLogger(FeatureData.class);
    private final DefaultFeatureCollection collection;
    private final String description;
    private final Geometries geometryType;
    private final Integer radius;

    public FeatureData(String filePath, List<String> endLocationDescriptions) throws IOException {
        GeoPackage geopkg = new GeoPackage(openFile(filePath));
        FeatureEntry entry = geopkg.features().get(0);
        this.geometryType = entry.getGeometryType();
        this.description = entry.getDescription();

        boolean polygons = this.geometryType.equals(Geometries.POLYGON) || this.geometryType.equals(Geometries.MULTIPOLYGON);
        if(polygons) {
            if(this.description.startsWith("grid_") && this.description.endsWith("m")) {
                this.radius = Integer.parseInt(this.description.substring(5,this.description.length() - 1));
                log.info("Reading grid with side length " +  this.radius + " meters.");
            } else {
                this.radius = 0;
                log.info("Reading polygons: " + this.description);
            }
        } else if (this.geometryType.equals(Geometries.POINT)) {
            this.radius = null;
            log.info("Reading points: " + this.description);
        } else {
            throw new IOException("Input geopackage has geometry type other than polygon or point. Cannot proceed with calculation.");
        }

        // Define new feature type
        SimpleFeatureReader r = geopkg.reader(entry, null,null);
        SimpleFeatureType schema = r.getFeatureType();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(schema.getName());
        builder.setSuperType((SimpleFeatureType) schema.getSuper());
        builder.addAll(schema.getAttributeDescriptors());
        boolean needsCentroids = polygons && (builder.get("centroid_x") == null || builder.get("centroid_y") == null);
        if(needsCentroids) {
            builder.add("centroid_x",Double.class);
            builder.add("centroid_y",Double.class);
        }
        if(polygons) {
            builder.add("nodes_within",Integer.class);
        }
        builder.add("nodeA",String.class);
        builder.add("nodeB",String.class);
        builder.add("costA",Double.class);
        builder.add("costB",Double.class);
        for(String description : endLocationDescriptions) {
            builder.add("accessibility_" + description,Double.class);
            builder.add("normalised_" + description,Double.class);
        }

        SimpleFeatureType newSchema = builder.buildFeatureType();

        // Create set of zones with updated feature types
        this.collection = new DefaultFeatureCollection("zones",newSchema);
        while(r.hasNext()) {
            SimpleFeature feature = DataUtilities.reType(newSchema,r.next());
            this.collection.add(feature);
        }

        // Close
        r.close();
        geopkg.close();

        // Add centroids if necessary
        if(needsCentroids) {
            log.info("No centroid attributes found. Calculating centroids for all zones and storing as attributes...");
            for(SimpleFeature zone : this.collection) {
                Point centroid = ((Geometry) zone.getDefaultGeometry()).getCentroid();
                zone.setAttribute("centroid_x",centroid.getX());
                zone.setAttribute("centroid_y",centroid.getY());
            }
        }
    }

    public DefaultFeatureCollection getCollection() {
        return this.collection;
    }

    public String getDescription() {
        return description;
    }

    public int getRadius() {
        return radius == null ? 0 : radius;
    }

    public Geometries getGeometryType() {
        return this.geometryType;
    }

    private static File openFile(String filePath) {
        File file = new File(filePath);
        if(!file.exists()) {
            throw new RuntimeException("File " + filePath + " not found!");
        }
        return file;
    }
}
