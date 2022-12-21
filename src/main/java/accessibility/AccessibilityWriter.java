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
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.io.IOUtils;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Helper methods to write and read matrices as CSV files (well, actually semi-colon separated files).
 *
 * @author mrieser / SBB
 */
public final class AccessibilityWriter {

    private final static Logger log = Logger.getLogger(AccessibilityWriter.class);

    private final static String COORDINATE_SYSTEM = "EPSG:27700";

    private final static String SEP = ",";
    private final static String NL = "\n";

    public static void writeNodesAsCsv(Map<Node,Double> accessibilityData, String filename) throws IOException {

        double min = Collections.min(accessibilityData.values());
        double max = Collections.max(accessibilityData.values());
        double diff = max-min;

        try (BufferedWriter writer = IOUtils.getBufferedWriter(filename)) {
            writer.write("ZONE" + SEP + "ACCESSIBILITY" + SEP + "NORMALISED" + NL);

            for (Map.Entry<Node,Double> e : accessibilityData.entrySet()) {
                writer.write(e.getKey().getId().toString());
                writer.append(SEP);
                writer.write(Double.toString(e.getValue()));
                writer.write(SEP);
                writer.write(Double.toString((e.getValue() - min)/diff));
                writer.append(NL);
            }
            writer.flush();
        }
    }

    public static void writeNodesAsGpkg(Map<Node,Double> accessibilityData, String filename) throws IOException {

        double min = Collections.min(accessibilityData.values());
        double max = Collections.max(accessibilityData.values());
        double diff = max-min;

        final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        final SimpleFeatureType TYPE = createNodeFeatureType();
        final SimpleFeatureBuilder builder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("Nodes",TYPE);

        // Convert map entries to feature data to create a feature collection
        for(Map.Entry<Node,Double> e : accessibilityData.entrySet()) {
            Coord c = e.getKey().getCoord();
            Point p = geometryFactory.createPoint(new Coordinate(c.getX(),c.getY(),c.getZ()));
            builder.add(p); // point geometry
            builder.add(e.getValue()); // accessibility
            builder.add((e.getValue() - min) / diff); // normalised accessibility
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

    private static SimpleFeatureType createNodeFeatureType() {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Nodes");

        // Specify coordinate ssystem
        try {
            builder.setCRS(CRS.decode(COORDINATE_SYSTEM));
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }

        // add attributes in order
        builder.add("Node", Point.class);
        builder.add("Accessibility",Double.class);
        builder.add("Normalised",Double.class);

        // build the type
        return builder.buildFeatureType();
    }
}
