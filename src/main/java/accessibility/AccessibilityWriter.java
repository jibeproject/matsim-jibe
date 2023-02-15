/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package accessibility;

import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureReader;
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
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.io.IOUtils;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

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
    private final static GeometryFactory GEOMETRY_FACTORY = JTSFactoryFinder.getGeometryFactory();
    private final static char SEP = ',';
    private final static char NL = '\n';

    public static void writeNodesAsCsv(IdMap<Node,Double> accessibilityData, String filename) throws IOException {

        double min = Collections.min(accessibilityData.values());
        double max = Collections.max(accessibilityData.values());
        double diff = max-min;

        BufferedWriter writer = IOUtils.getBufferedWriter(filename);
        writer.append("NODE" + SEP + "ACCESSIBILITY" + SEP + "NORMALISED" + NL);
        for (Map.Entry<Id<Node>,Double> e : accessibilityData.entrySet()) {
            writer.append(e.getKey().toString());
            writer.append(SEP);
            writer.append(Double.toString(e.getValue()));
            writer.append(SEP);
            writer.append(Double.toString((e.getValue() - min)/diff));
            writer.append(NL);
        }
        writer.flush();
    }

    public static void writeNodesAsGpkg(IdMap<Node,Double> accessibilityData, Network network, String filename) throws IOException {

        final double min = Collections.min(accessibilityData.values());
        final double max = Collections.max(accessibilityData.values());
        final double diff = max-min;

        final SimpleFeatureType TYPE = createNodeFeatureType();
        final SimpleFeatureBuilder builder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("Nodes",TYPE);

        // Convert map entries to feature data to create a feature collection
        for(Map.Entry<Id<Node>,Double> e : accessibilityData.entrySet()) {
            Node node = network.getNodes().get(e.getKey());
            Coord c = node.getCoord();
            Point p = GEOMETRY_FACTORY.createPoint(new Coordinate(c.getX(),c.getY(),c.getZ()));
            builder.add(p); // point geometry
            builder.add(Integer.parseInt(e.getKey().toString())); // node ID todo: check this works
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

        // Specify coordinate system
        try {
            builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }

        // add attributes in order
        builder.add("Node", Point.class);
        builder.add("Id",Integer.class);
        builder.add("Accessibility",Double.class);
        builder.add("Normalised",Double.class);

        // build the type
        return builder.buildFeatureType();
    }

    private static SimpleFeatureType createZoneFeatureType(String originalFilePath) throws IOException {

        // Get base type from original file
        GeoPackage geopkg = new GeoPackage(new File(originalFilePath));
        SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);

        SimpleFeatureType schema = r.getFeatureType();

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(schema.getName());
        builder.setSuperType((SimpleFeatureType) schema.getSuper());
        builder.addAll(schema.getAttributeDescriptors());
        builder.add("accessibility",Double.class);
        builder.add("normalised",Double.class);


        // build the type
        return builder.buildFeatureType();
    }
}
