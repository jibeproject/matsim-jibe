package estimation.dynamic;


import gis.GpkgReader;
import org.apache.log4j.Logger;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import resources.Properties;
import resources.Resources;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.TripRouteWriter.drawPath;

public class DynamicActive implements DynamicComponent {

    final DynamicComponent dynamicBike;
    final DynamicComponent dynamicWalk;

    static Logger logger = Logger.getLogger(DynamicActive.class);

    public DynamicActive(DynamicComponent dynamicBike, DynamicComponent dynamicWalk) {
        this.dynamicBike = dynamicBike;
        this.dynamicWalk = dynamicWalk;
    }

    @Override
    public void update(double[] x) {
        dynamicBike.update(x);
        dynamicWalk.update(x);
    }

    @Override
    public String getStats() {
        return dynamicBike.getStats() + "\n" + dynamicWalk.getStats();
    }

    public void mapRoutes(String outputGpkg, String[] ids, Network network) throws FactoryException, IOException {

        Map<Integer, SimpleFeature> networkFeatures = GpkgReader.readEdges();

        final SimpleFeatureType TYPE = createRouteFeatureType();
        final SimpleFeatureBuilder builder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("route",TYPE);

        // Get paths
        for(RouteDataDynamic dynamicComponent : List.of((RouteDataDynamic) dynamicBike,(RouteDataDynamic) dynamicWalk)) {
            RouteDataDynamic.PathData paths = dynamicComponent.getPathData();
            for(int i = 0 ; i < paths.length ; i++) {
                if(paths.tooShort[i]) {
                    continue;
                }
                List<Id<Link>> links = paths.path[i].links.stream().map(Identifiable::getId).toList();
                builder.add(drawPath(links, network, networkFeatures));
                builder.add(ids[i]);
                builder.add(dynamicComponent.mode);
                builder.add(paths.time[i]);
                collection.add(builder.buildFeature(null));
            }
        }

        // Write Geopackage
        File outputFile = new File(outputGpkg);
        if(outputFile.delete()) {
            logger.warn("File " + outputFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputFile);
        out.init();
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription("routes");
        out.add(entry,collection);
        out.createSpatialIndex(entry);
        out.close();
    }

    private static SimpleFeatureType createRouteFeatureType() throws FactoryException {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Routes");
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));
        builder.add("Path", LineString.class);
        builder.length(20).add("id",String.class);
        builder.length(10).add("mode",String.class);
        builder.add("time",Double.class);
        return builder.buildFeatureType();
    }


}
