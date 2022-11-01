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

    public static Map<String,Map<Integer, SimpleFeature>> read(File gpkgFile) {
        Map<String, Map<Integer,SimpleFeature>> network = new HashMap<>();

        Map<Integer,SimpleFeature> edges = new HashMap<>();
        Map<Integer,SimpleFeature> nodes = new HashMap<>();

        network.put("edges",edges);
        network.put("nodes",nodes);

        try{
            GeoPackage geopkg = new GeoPackage(gpkgFile);

            // Read edges
            SimpleFeatureReader edgesReader = geopkg.reader(geopkg.feature("links"), null,null);
            while(edgesReader.hasNext()) {
                SimpleFeature edge = edgesReader.next();
                edges.put((int) edge.getAttribute("edgeID"),edge);
            }
            edgesReader.close();

            // Read nodes
            SimpleFeatureReader nodesReader = geopkg.reader(geopkg.feature("nodes"), null,null);
            while(nodesReader.hasNext()) {
                SimpleFeature node = nodesReader.next();
                nodes.put((int) node.getAttribute("nodeID"),node);
            }
            nodesReader.close();

            // Close gpkg
            geopkg.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return network;
    }

}
