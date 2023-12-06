/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility;

import org.apache.log4j.Logger;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper methods to write and read matrices as CSV files (well, actually semi-colon separated files).
 *
 * @author mrieser / SBB
 */
public final class AccessibilityWriter {

    private final static Logger log = Logger.getLogger(AccessibilityWriter.class);
    private final static GeometryFactory GEOMETRY_FACTORY = JTSFactoryFinder.getGeometryFactory();
    private final static char SEP = ',';
    private final static char NL = '\n';

    public static void writeNodesAsGpkg(Map<Id<Node>, double[]> accessibilityData, List<String> endLocationDescriptions, Network network, String filename) throws IOException {

        final SimpleFeatureType TYPE = createNodeFeatureType(endLocationDescriptions);
        final SimpleFeatureBuilder builder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("Nodes",TYPE);

        int endLocationCount = endLocationDescriptions.size();

        double[] mins = new double[endLocationCount];
        double[] maxs = new double[endLocationCount];
        double[] diffs = new double[endLocationCount];

        for(int i = 0 ; i < endLocationCount ; i++) {
            int finalI = i;
            Set<Double> result = accessibilityData.values().stream().map(v -> v[finalI]).collect(Collectors.toSet());
            mins[i] = Collections.min(result);
            maxs[i] = Collections.max(result);
            diffs[i] = maxs[i] - mins[i];
        }

        // Convert map entries to feature data to create a feature collection
        for(Map.Entry<Id<Node>,double[]> e : accessibilityData.entrySet()) {
            Node node = network.getNodes().get(e.getKey());
            Coord c = node.getCoord();
            Point p = GEOMETRY_FACTORY.createPoint(new Coordinate(c.getX(),c.getY(),c.getZ()));
            builder.add(p); // point geometry
            builder.add(Integer.parseInt(e.getKey().toString())); // node ID
            double[] values = e.getValue();
            for(int i = 0 ; i < endLocationCount ; i++) {
                builder.add(values[i]);
                builder.add((values[i] - mins[i]) / diffs[i]);
            }
            collection.add(builder.buildFeature(null));
        }

        // Write Geopackage
        File outputFile = new File(filename);
        if(outputFile.delete()) {
            log.warn("File " + outputFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputFile);
        out.init();
        FeatureEntry nodes = new FeatureEntry();
        nodes.setDescription("nodes");
        out.add(nodes,collection);
        out.createSpatialIndex(nodes);
        out.close();
    }

    private static SimpleFeatureType createNodeFeatureType(List<String> endLocationDescriptions) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Nodes");

        // Specify coordinate system
        try {
            builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }

        // add attributes in order
        builder.add("node", Point.class);
        builder.add("id",Integer.class);

        for(String description : endLocationDescriptions) {
            builder.add("accessibility_" + description,Double.class);
            builder.add("normalised_" + description,Double.class);
        }

        // build the type
        return builder.buildFeatureType();
    }
}
