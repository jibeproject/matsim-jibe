package gis;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import resources.Properties;
import resources.Resources;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// Tools for reading edges and nodes files produced by the JIBE WP2 team

public class GpkgReader {

    public static Map<Integer, SimpleFeature> readNodes(File nodesFile) {

        Map<Integer,SimpleFeature> nodes = new HashMap<>();

        try{
            GeoPackage geopkg = new GeoPackage(nodesFile);
            SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);
            while(r.hasNext()) {
                SimpleFeature node = r.next();
                nodes.put((int) node.getAttribute("nodeID"),node);
            }
            r.close();
            geopkg.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return nodes;

    }

    public static Map<Integer, SimpleFeature> readEdges(File edgesFile) {

        Map<Integer,SimpleFeature> edges = new HashMap<>();

        try{
            GeoPackage geopkg = new GeoPackage(edgesFile);
            SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);
            while(r.hasNext()) {
                SimpleFeature edge = r.next();
                edges.put((int) edge.getAttribute("edgeID"),edge);
            }
            r.close();
            geopkg.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return edges;

    }

    public static Geometry readRegionBoundary() throws IOException {
        return readBoundary(Resources.instance.getString(Properties.REGION_BOUNDARY));
    }

    public static Geometry readNetworkBoundary() throws IOException {
        return readBoundary(Resources.instance.getString(Properties.NETWORK_BOUNDARY));
    }

    public static Geometry readBoundary(String filePath) throws IOException {
        GeoPackage geopkg = new GeoPackage(openFile(filePath));
        SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);
        SimpleFeature f = r.next();
        Geometry boundary = (Geometry) f.getDefaultGeometry();
        r.close();
        geopkg.close();
        return boundary;
    }

    public static DefaultFeatureCollection readGridAndUpdateFeatureType(String filePath) throws IOException {

        GeoPackage geopkg = new GeoPackage(openFile(filePath));
        SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);

        // Define new feature type
        SimpleFeatureType schema = r.getFeatureType();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(schema.getName());
        builder.setSuperType((SimpleFeatureType) schema.getSuper());
        builder.addAll(schema.getAttributeDescriptors());
        builder.add("nodes_within",Integer.class);
        builder.add("connector_node",Integer.class);
        builder.add("connector_dist",Double.class);
        builder.add("connector_marg_disutility",Double.class);
        builder.add("connector_disutility",Double.class);
        builder.add("connector_adj",Double.class);
        builder.add("accessibility",Double.class);
        builder.add("normalised",Double.class);
        SimpleFeatureType newSchema = builder.buildFeatureType();

        // Create set of zones with updated feature types
        DefaultFeatureCollection collection = new DefaultFeatureCollection("Zones",newSchema);

        while(r.hasNext()) {
            SimpleFeature zone = DataUtilities.reType(newSchema,r.next());
//            Point centroid = ((Polygon) zone.getDefaultGeometry()).getCentroid();
//            zone.setAttribute("centroid_x",centroid.getX());
//            zone.setAttribute("centroid_y",centroid.getY());
            collection.add(zone);
        }

        r.close();
        geopkg.close();

        return collection;
    }

    private static File openFile(String filePath) {
        File file = new File(filePath);
        if(!file.exists()) {
            throw new RuntimeException("File " + filePath + " not found!");
        }
        return file;
    }

}
