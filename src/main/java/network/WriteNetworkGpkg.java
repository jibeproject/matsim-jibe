package network;

import routing.Gradient;
import routing.travelTime.WalkTravelTime;
import data.Crossing;
import routing.disutility.components.LinkAttractiveness;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkStress;
import com.google.common.math.LongMath;
import org.apache.commons.lang3.ArrayUtils;
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
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelTime;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

// Writes network with links in both directions (useful for visualisations where out/return details are different)

public class WriteNetworkGpkg {

    private final static Logger log = Logger.getLogger(WriteNetworkGpkg.class);

    public static void main(String[] args) throws FactoryException, IOException {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 or 4 arguments:\n" +
                    "(0) MATSim network file (.xml)\n" +
                    "(1) Input network (.gpkg)\n" +
                    "(2) Output edges (.gpkg)\n");
        }

        String matsimNetworkPath = args[0];
        File networkGpkgPath = new File(args[1]);
        File outputEdgesFile = new File(args[2]);

        // Read edges
        Map<Integer,SimpleFeature> edges = GpkgReader.read(networkGpkgPath).get("edges");

        // Read MATSim network
        log.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(matsimNetworkPath);

        // Walk travel time
        TravelTime ttWalk = new WalkTravelTime();

        // Prepare geopackage data
        final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        final SimpleFeatureType TYPE = createFeatureType();
        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection collection = new DefaultFeatureCollection("Routes",TYPE);

        // Write directed MATSim network as gpkg
        int counter = 0;
        int forwardLinks = 0;
        int backwardLinks = 0;
        for (Link link : network.getLinks().values()) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                log.info("Processing link " + counter + " / " + network.getLinks().size());
            }
            int edgeID = (int) link.getAttributes().getAttribute("edgeID");
            boolean fwd = (boolean) link.getAttributes().getAttribute("fwd");
            Coord fromNode = link.getFromNode().getCoord();
            Coord toNode = link.getToNode().getCoord();
            SimpleFeature edge = edges.get(edgeID);
            Coordinate[] coords = new Coordinate[0];
            try {
                coords = ((LineString) edge.getDefaultGeometry()).getCoordinates().clone();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Check direction matches from/to node and reverse if necessary
            Coordinate fromCoord = coords[0];
            Coordinate toCoord = coords[coords.length - 1];
            Coordinate fromNodeCoord = new Coordinate(fromNode.getX(),fromNode.getY());
            Coordinate toNodeCoord = new Coordinate(toNode.getX(),toNode.getY());
            if ((fwd && fromCoord.equals2D(fromNodeCoord) && toCoord.equals2D(toNodeCoord)) ||
                    (!fwd && fromCoord.equals2D(toNodeCoord) && toCoord.equals2D(fromNodeCoord))) {
                forwardLinks++;
            } else if ((fwd && fromCoord.equals2D(toNodeCoord) && toCoord.equals2D(fromNodeCoord)) ||
                    (!fwd && fromCoord.equals2D(fromNodeCoord) && toCoord.equals2D(toNodeCoord))) {
                backwardLinks++;
                ArrayUtils.reverse(coords);
            } else {
                throw new RuntimeException("Edge " + edgeID + " doesn't match its from and to nodes!");
            }

            // Length, travelTime, travelDisutility
            double length = link.getLength();
            double walkTime = ttWalk.getLinkTravelTime(link,0,null,null);

            // Reverse if not in forward direction
            if(!fwd) {
                ArrayUtils.reverse(coords);
            }

            // Geometry
            featureBuilder.add(geometryFactory.createLineString(coords));

            // Other attributes
            featureBuilder.add(edgeID);
            featureBuilder.add(link.getAttributes().getAttribute("osmID"));
            featureBuilder.add(fwd);
            featureBuilder.add(length / walkTime * 3.6);
            featureBuilder.add(link.getAttributes().getAttribute("speedLimitMPH"));
            featureBuilder.add((int) link.getNumberOfLanes());
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.car));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.bike));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.walk));
            featureBuilder.add(Gradient.getGradient(link));
            featureBuilder.add(link.getAttributes().getAttribute("endsAtJct"));
            featureBuilder.add(link.getAttributes().getAttribute("crossVehicles"));
            featureBuilder.add(Crossing.getType(link, "walk").toString());
            featureBuilder.add(link.getAttributes().getAttribute("crossLanes"));
            featureBuilder.add(link.getAttributes().getAttribute("crossSpeedLimitMPH"));
            featureBuilder.add(LinkAttractiveness.getAttractiveness(link,false));
            featureBuilder.add(LinkAttractiveness.getAttractiveness(link,true));
            featureBuilder.add(LinkStress.getWalkStress(link));
            featureBuilder.add(JctStress.getWalkJunctionStress(link));
            SimpleFeature feature = featureBuilder.buildFeature(null);
            collection.add(feature);
        }

        log.info(forwardLinks + " edges in the correct direction");
        log.info(backwardLinks + " edges in wrong direction needed to be reversed");

        // Write Geopackage
        if(outputEdgesFile.delete()) {
            log.warn("File " + outputEdgesFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputEdgesFile);
        out.init();
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription("network");
        out.add(entry,collection);
        out.createSpatialIndex(entry);
        out.close();
    }

    private static SimpleFeatureType createFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("links");
        builder.setCRS(CRS.decode("EPSG:27700")); // <- Coordinate reference system

        // add attributes in order
        builder.add("path", LineString.class);
        builder.add("edgeID",Integer.class);
        builder.add("osmID",String.class);
        builder.add("fwd",Boolean.class);
        builder.add("walkSpeedKPH",Double.class);
        builder.add("carSpeedLimitMPH",Double.class);
        builder.add("lanes",Integer.class);
        builder.add("car",Boolean.class);
        builder.add("bike",Boolean.class);
        builder.add("walk",Boolean.class);
        builder.add("gradient",Double.class);
        builder.add("endsAtJct",Boolean.class);
        builder.add("crossesVehicles",Boolean.class);
        builder.add("crossingTypeWalk",String.class);
        builder.add("crossingLanes",Double.class);
        builder.add("crossingSpeedLimit",Double.class);
        builder.add("f_attractiveness_day",Double.class);
        builder.add("f_attractiveness_night",Double.class);
        builder.add("f_walkStress",Double.class);
        builder.add("f_walkStressJct",Double.class);

        return builder.buildFeatureType();
    }

}
