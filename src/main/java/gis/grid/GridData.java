package gis.grid;

import org.apache.log4j.Logger;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.IOException;

// Tools for reading edges and nodes files produced by the JIBE WP2 team

public class GridData {

    private final static Logger log = Logger.getLogger(GridData.class);
    private final DefaultFeatureCollection grid;
    private final String description;
    private final int sideLength;

    public GridData(String filePath) throws IOException {
        GeoPackage geopkg = new GeoPackage(openFile(filePath));
        FeatureEntry entry = geopkg.features().get(0);
        this.description = entry.getDescription();
        if(!this.description.startsWith("grid_") || !this.description.endsWith("m")) {
            throw new RuntimeException("Invalid grid feature description!\n" +
                    "Did you create the grid using CreateGridCellGpkg.java?");
        }

        // Get side length (only works if
        this.sideLength = Integer.parseInt(this.description.substring(5,this.description.length() - 1));
        log.info("Read grid with side length " +  this.sideLength + " meters.");

        // Define new feature type
        SimpleFeatureReader r = geopkg.reader(entry, null,null);
        SimpleFeatureType schema = r.getFeatureType();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(schema.getName());
        builder.setSuperType((SimpleFeatureType) schema.getSuper());
        builder.addAll(schema.getAttributeDescriptors());
        builder.add("nodes_within",Integer.class);
        builder.add("connector_node",Integer.class);
        builder.add("connector_dist",Double.class);
        builder.add("connector_cost",Double.class);
        builder.add("connector_time",Double.class);
        builder.add("accessibility",Double.class);
        builder.add("normalised",Double.class);
        SimpleFeatureType newSchema = builder.buildFeatureType();

        // Create set of zones with updated feature types
        this.grid = new DefaultFeatureCollection("grid",newSchema);
        while(r.hasNext()) {
            SimpleFeature zone = DataUtilities.reType(newSchema,r.next());
            this.grid.add(zone);
        }

        // Close
        r.close();
        geopkg.close();
    }

    public DefaultFeatureCollection getGrid() {
        return this.grid;
    }

    public String getDescription() {
        return description;
    }

    public int getSideLength() {
        return this.sideLength;
    }

    private static File openFile(String filePath) {
        File file = new File(filePath);
        if(!file.exists()) {
            throw new RuntimeException("File " + filePath + " not found!");
        }
        return file;
    }

}
