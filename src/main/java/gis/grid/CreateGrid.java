package gis.grid;

import gis.GisUtils;
import gis.GpkgReader;
import gis.IntersectionGridBuilder;
import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.grid.GridFeatureBuilder;
import org.geotools.grid.Grids;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.IOException;

public class CreateGrid {

    private final static Logger log = Logger.getLogger(CreateGrid.class);

    public static void main(String[] args) throws FactoryException, IOException {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments: " +
                    "(0) Properties file\n" +
                    "(1) Hex side length in metres\n" +
                    "(2) Output file name");
        }

        Resources.initializeResources(args[0]);
        int sideLengthMetres = Integer.parseInt(args[1]);
        String outputFile = args[2];

        String coordinateSystem = Resources.instance.getString(Properties.COORDINATE_SYSTEM);

        // Read origin boundary file
        log.info("Reading grid boundary file...");
        Geometry boundary = GpkgReader.readRegionBoundary();

        // Define grid bounds
        log.info("Preparing grid...");
        ReferencedEnvelope gridBounds = new ReferencedEnvelope(boundary.getEnvelopeInternal(), CRS.decode(coordinateSystem));

        // Set up feature type
        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName("grid");
        tb.add(GridFeatureBuilder.DEFAULT_GEOMETRY_ATTRIBUTE_NAME,
                Polygon.class,
                CRS.decode(coordinateSystem));
        tb.add("id",Integer.class);
        tb.add("centroid_x",Double.class);
        tb.add("centroid_y",Double.class);
        SimpleFeatureType TYPE = tb.buildFeatureType();

        // Build grid
        IntersectionGridBuilder builder = new IntersectionGridBuilder(TYPE,boundary);
        SimpleFeatureSource grid = Grids.createHexagonalGrid(gridBounds,sideLengthMetres,-1,builder);

        // Write grid to gpkg
        log.info("Writing grid to gpkg");
        String description = "grid_" + sideLengthMetres + "m";
        GisUtils.writeFeaturesToGpkg(grid.getFeatures(),description,outputFile);
    }
}
