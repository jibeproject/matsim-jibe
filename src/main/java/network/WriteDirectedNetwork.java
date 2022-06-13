package network;

import bicycle.BicycleTravelTime;
import bicycle.speed.BicycleLinkSpeedCalculatorGradOnlyImpl;
import bicycle.jibe.CustomBicycleUtils;
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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.FactoryException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Writes network with links in both directions (useful for visualisations where out/return details are different)

public class WriteDirectedNetwork {

    private final static Logger log = Logger.getLogger(WriteDirectedNetwork.class);
    private final static double MAX_BIKE_SPEED = 16 / 3.6;

    public static void main(String[] args) throws FactoryException, IOException {

        if(args.length != 3) {
            throw new RuntimeException("Program requires 3 arguments:\n" +
                    "(0) MATSim network file path\n" +
                    "(1) Input edges gpkg\n" +
                    "(2) Output edges gpkg");
        }

        String matsimNetworkPath = args[0];
        File edgesFile = new File(args[1]);
        File outputEdgesFile = new File(args[2]);

        // Read edges
        Map<Integer,SimpleFeature> edges = GpkgReader.readEdges(edgesFile);

        // Read MATSim network
        log.info("Reading MATSim network...");
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(matsimNetworkPath);

        // Use bicycle only network
//        Network modeSpecificNetwork = NetworkUtils.createNetwork();
//        new TransportModeNetworkFilter(network).filter(modeSpecificNetwork, Collections.singleton(TransportMode.bike));
//        network = modeSpecificNetwork;

        // Setup config
        Config config = ConfigUtils.createConfig();
        BicycleConfigGroup bicycleConfigGroup = new BicycleConfigGroup();
        bicycleConfigGroup.setBicycleMode("bike");
        config.addModule(bicycleConfigGroup);
        PlanCalcScoreConfigGroup planCalcScoreConfigGroup = new PlanCalcScoreConfigGroup();
        log.info("Marginal utility of Money = " + planCalcScoreConfigGroup.getMarginalUtilityOfMoney());

        // Create bicycle vehicle
        VehicleType type = VehicleUtils.createVehicleType(Id.create("bike", VehicleType.class));
        type.setMaximumVelocity(MAX_BIKE_SPEED);
        Vehicle bike = VehicleUtils.createVehicle(Id.createVehicleId(1), type);

        // Set up bicycle data
        BicycleLinkSpeedCalculatorGradOnlyImpl linkSpeedCalculator = new BicycleLinkSpeedCalculatorGradOnlyImpl((BicycleConfigGroup) config.getModules().get(BicycleConfigGroup.GROUP_NAME));
        TravelTime ttCycle = new BicycleTravelTime(linkSpeedCalculator);

        // Prepare geopackage data todo: filter descriptors
        final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        final List<AttributeDescriptor> descriptors = edges.entrySet().iterator().next().getValue().getFeatureType().getAttributeDescriptors();
        final SimpleFeatureType TYPE = createFeatureType(descriptors);
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
            double cycleTime = ttCycle.getLinkTravelTime(link,0,null,bike);
            double cycleDisutilityBerlin = 0.; //; = tdBerlin.getLinkTravelDisutility(link,0,null,bike);
            double cycleDisutilityJibe = 0.; // = tdJibe.getLinkTravelDisutility(link,0,null,bike);


            // Reverse if not in forward direction
            if(!fwd) {
                ArrayUtils.reverse(coords);
            }

            // Geometry
            featureBuilder.add(geometryFactory.createLineString(coords));

            // Other attributes
//            Iterator<AttributeDescriptor> it = descriptors.iterator();
//            it.next(); // skip geometry
//            while(it.hasNext()) {
//                featureBuilder.add(edge.getAttribute(it.next().getLocalName()));
//            }
            featureBuilder.add(edgeID);
            featureBuilder.add(link.getAttributes().getAttribute("osmID"));
            featureBuilder.add(fwd);
//            featureBuilder.add(Integer.parseInt(link.getFromNode().getId().toString()));
//            featureBuilder.add(Integer.parseInt(link.getToNode().getId().toString()));
//            featureBuilder.add(length);
//            featureBuilder.add(link.getFromNode().getCoord().getZ());
//            featureBuilder.add(link.getToNode().getCoord().getZ());
//            featureBuilder.add(linkSpeedCalculator.computeGradientFactor(link));
//            featureBuilder.add(cycleTime);
            featureBuilder.add(length / cycleTime * 3.6);
//            featureBuilder.add((double) link.getAttributes().getAttribute("bikeSpeed") * 3.6);
//            featureBuilder.add(!((Double) link.getAttributes().getAttribute("bikeSpeed")).isNaN());
//            featureBuilder.add(CustomBicycleUtils.getStress(link).toString());
//            featureBuilder.add(cycleDisutilityBerlin / length);
//            featureBuilder.add(cycleDisutilityJibe / length);
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.car));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.bike));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.walk));
            featureBuilder.add(CustomBicycleUtils.getCycleProtectionType(link).toString());
            featureBuilder.add(CustomBicycleUtils.getVgviFactor(link));
            featureBuilder.add(CustomBicycleUtils.getLightingFactor(link));
            featureBuilder.add(CustomBicycleUtils.getShannonFactor(link));
            featureBuilder.add(CustomBicycleUtils.getCrimeFactor(link));
            featureBuilder.add(CustomBicycleUtils.getPoiFactor(link));
            featureBuilder.add(CustomBicycleUtils.getNegativePoiFactor(link));
            featureBuilder.add(CustomBicycleUtils.getFreightPoiFactor(link));
            featureBuilder.add(CustomBicycleUtils.getAttractiveness(link));
            featureBuilder.add(CustomBicycleUtils.getStress(link));
            featureBuilder.add(CustomBicycleUtils.getJunctionStress(link));
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

    private static SimpleFeatureType createFeatureType(List<AttributeDescriptor> descriptors) throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("links");
        builder.setCRS(CRS.decode("EPSG:27700")); // <- Coordinate reference system

        // add attributes in order todo: sort out bck/fwd
        //builder.addAll(descriptors);
        builder.add("path", LineString.class);
        builder.add("edgeID",Integer.class);
        builder.add("osmID",Integer.class);
        builder.add("fwd",Boolean.class);
//        builder.add("fromNode", String.class);
//        builder.add("toNode",String.class);
//        builder.add("lengthM",Double.class);
//        builder.add("startZ",Double.class);
//        builder.add("endZ",Double.class);
//        builder.add("cycleGradFactor",Double.class);
//        builder.add("cycleTimeSmatsim",Double.class);
        builder.add("cycleSpeedKPH",Double.class);
//        builder.add("cycleKPHstrava",Double.class);
//        builder.add("strava",Boolean.class);
//        builder.add("cycleSafety",String.class);
//        builder.add("cycleDisutilityBerlin",Double.class);
//        builder.add("cycleDisutilityJibe",Double.class);
        builder.add("car",Boolean.class);
        builder.add("bike",Boolean.class);
        builder.add("walk",Boolean.class);
        builder.add("protectionType",String.class);
        builder.add("f_vgvi",Double.class);
        builder.add("f_lighting",Double.class);
        builder.add("f_shannon",Double.class);
        builder.add("f_crime",Double.class);
        builder.add("f_POIs",Double.class);
        builder.add("f_negPOIs",Double.class);
        builder.add("f_freightPOIs",Double.class);
        builder.add("f_attractiveness",Double.class);
        builder.add("f_stress",Double.class);
        builder.add("f_jctStress",Double.class);


        // build the type
        final SimpleFeatureType TYPE = builder.buildFeatureType();

        return TYPE;
    }

}
