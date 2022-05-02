package network;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.GeoPackage;
import org.opengis.feature.simple.SimpleFeature;

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

}
