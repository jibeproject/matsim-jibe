package gis;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
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

    private static File openFile(String filePath) {
        File file = new File(filePath);
        if(!file.exists()) {
            throw new RuntimeException("File " + filePath + " not found!");
        }
        return file;
    }

}
