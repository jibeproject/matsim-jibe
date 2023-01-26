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

import java.io.IOException;

public class CreateGridCellGpkg {

    private final static Logger log = Logger.getLogger(CreateGridCellGpkg.class);
    private final static String COORDINATE_SYSTEM = "EPSG:27700";


    public static void main(String[] args) throws FactoryException, IOException {
        String gridBoundaryFile = args[0];
        String outputFile = args[1];
        double sideLengthMetres = Double.parseDouble(args[2]);

        // Read origin boundary file
        log.info("Reading grid boundary file...");
        Geometry boundary = GpkgReader.readBoundary(gridBoundaryFile);

        // Creating grid
        log.info("Preparing grid...");
        ReferencedEnvelope gridBounds = new ReferencedEnvelope(boundary.getEnvelopeInternal(), CRS.decode(COORDINATE_SYSTEM));
        SimpleFeatureType TYPE = createGridFeatureType();
        IntersectionGridBuilder builder = new IntersectionGridBuilder(TYPE,boundary);
        SimpleFeatureSource grid = Grids.createHexagonalGrid(gridBounds,sideLengthMetres,-1,builder);

        // Write grid to gpkg
        log.info("Writing grid to gpkg");
        GisUtils.writeFeaturesToGpkg(grid.getFeatures(),outputFile);
    }

    private static SimpleFeatureType createGridFeatureType() throws FactoryException {
        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName("grid");
        tb.add(GridFeatureBuilder.DEFAULT_GEOMETRY_ATTRIBUTE_NAME,
                Polygon.class,
                CRS.decode(COORDINATE_SYSTEM));
        tb.add("id",Integer.class);
        tb.add("centroid_x",Double.class);
        tb.add("centroid_y",Double.class);
        return tb.buildFeatureType();
    }
}
