package gis;

import org.apache.log4j.Logger;
import org.geotools.data.simple.SimpleFeatureCollection;
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

public class CreateGridCellGpkg {

    private final static Logger log = Logger.getLogger(CreateGridCellGpkg.class);

    public static void main(String[] args) throws FactoryException, IOException {

        if(args.length != 1) {
            throw new RuntimeException("Program requires 1 argument: Properties file");
        }

        Resources.initializeResources(args[0]);

        String outputFile = Resources.instance.getString(Properties.HEX_GRID);
        String coordinateSystem = Resources.instance.getString(Properties.COORDINATE_SYSTEM);
        double sideLengthMetres = Resources.instance.getDouble(Properties.HEX_SIDE_LENGTH);

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
        GisUtils.writeFeaturesToGpkg(grid.getFeatures(),outputFile);
    }
}
