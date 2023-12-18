package network;

import gis.GpkgReader;
import org.matsim.api.core.v01.Id;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import resources.Properties;
import resources.Resources;
import routing.Bicycle;
import routing.Gradient;
import routing.disutility.JibeDisutility3;
import routing.disutility.components.*;
import routing.travelTime.WalkTravelTime;
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
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

// Writes network with links in both directions (useful for visualisations where out/return details are different)

public class WriteNetworkGpkg {

    private final static Logger log = Logger.getLogger(WriteNetworkGpkg.class);

    public static void main(String[] args) throws FactoryException, IOException {

        if(args.length < 2 || args.length > 3) {
            throw new RuntimeException("Program requires 3 or 4 arguments:\n" +
                    "(0) Properties file (.properties)" +
                    "(1) Output edges (.gpkg)\n" +
                    "(2) OPTIONAL: mode (for printing a mode-specific network)");
        }

        Resources.initializeResources(args[0]);

        String outputEdgesFilePath = args[1];

        String modeFilter = null;
        if(args.length == 3) {
            modeFilter = args[2];
        }

        // Read MATSim network
        log.info("Reading MATSim network...");
        Network network = NetworkUtils2.readFullNetwork();

        // Filter network to a specific mode (if applicable)
        if(modeFilter != null) {
            Network modeSpecificNetwork = NetworkUtils.createNetwork();
            new TransportModeNetworkFilter(network).filter(modeSpecificNetwork, Collections.singleton(modeFilter));
            network = modeSpecificNetwork;
        }

        write(network,outputEdgesFilePath);
    }

    private static SimpleFeatureType createFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("links");
        builder.setCRS(CRS.decode(Resources.instance.getString(Properties.COORDINATE_SYSTEM)));

        // add attributes in order
        builder.add("path", LineString.class);
        builder.add("edgeID",Integer.class);
        builder.add("osmID",Integer.class);
        builder.add("linkID",String.class);
        builder.add("fwd",Boolean.class);
        builder.add("length",Double.class);
        builder.add("cycleTime",Double.class);
        builder.add("walkTime",Double.class);
        builder.add("freespeed",Double.class);
//        builder.add("carSpeedLimitMPH",Double.class);
//        builder.add("car85PercSpeedKPH",Double.class);
//        builder.add("bikeJibeMarginalDisutilityDay",Double.class);
//        builder.add("bikeJibeMarginalDisutilityNight",Double.class);
//        builder.add("walkJibeMarginalDisutilityDay",Double.class);
//        builder.add("walkJibeMarginalDisutilityNight",Double.class);
        builder.add("width",Double.class);
        builder.add("lanes",Integer.class);
        builder.add("aadt",Integer.class);
        builder.add("aadtFwd",Integer.class);
        builder.add("aadtFwd_car",Integer.class);
        builder.add("aadtFwd_truck",Integer.class);
        builder.add("car",Boolean.class);
        builder.add("bike",Boolean.class);
        builder.add("walk",Boolean.class);
        builder.add("motorway",Boolean.class);
        builder.add("trunk",Boolean.class);
        builder.add("dismount",Boolean.class);
//        builder.add("disconnected_car",Boolean.class);
//        builder.add("disconnected_bike",Boolean.class);
//        builder.add("disconnected_walk",Boolean.class);
        builder.add("gradient",Double.class);
        builder.add("bikeProtectionType",String.class);
//        builder.add("endsAtJct",Boolean.class);
//        builder.add("crossesVehicles",Boolean.class);
//        builder.add("crossingTypeBike",String.class);
//        builder.add("crossingTypeWalk",String.class);
//        builder.add("crossingLanes",Double.class);
//        builder.add("crossingWidth",Double.class);
//        builder.add("crossingAADT",Double.class);
//        builder.add("crossingSpeedLimit",Double.class);
//        builder.add("crossing85PercSpeed",Double.class);
        builder.add("vgvi",Double.class);
        builder.add("shannon",Double.class);
        builder.add("POIs",Double.class);
        builder.add("negPOIs",Double.class);
//        builder.add("crime",Double.class);
//        builder.add("streetLights",Integer.class);
//        builder.add("f_vgvi",Double.class);
//        builder.add("f_lighting",Double.class);
//        builder.add("f_shannon",Double.class);
//        builder.add("f_crime",Double.class);
//        builder.add("f_POIs",Double.class);
//        builder.add("f_negPOIs",Double.class);
        builder.add("freightPOIs",Integer.class);
//        builder.add("ambience_day",Double.class);
//        builder.add("ambience_night",Double.class);
        builder.add("bikeStressDiscrete",String.class);
        builder.add("bikeStress",Double.class);
        builder.add("bikeStressJct",Double.class);
        builder.add("walkStress",Double.class);
        builder.add("walkStressJct",Double.class);

        return builder.buildFeatureType();
    }

    public static void write(Network network, String outputEdgesFilePath) throws IOException, FactoryException {

        // Read edges
        Map<Integer,SimpleFeature> edges = GpkgReader.readEdges();

        // Set up bicycle data
        Bicycle bicycle = new Bicycle(null);
        Vehicle bike = bicycle.getVehicle();

        // Travel Times
        TravelTime ttBike = bicycle.getTravelTime();
        TravelTime ttWalk = new WalkTravelTime();

//        // Travel Disutilities
//        JibeDisutility3 tdJibeBikeDay = new JibeDisutility3(TransportMode.bike, ttBike,true);
//        JibeDisutility3 tdJibeBikeNight = new JibeDisutility3(TransportMode.bike, ttBike,false);
//
//        JibeDisutility3 tdJibeWalkDay = new JibeDisutility3(TransportMode.walk, ttWalk,true);
//        JibeDisutility3 tdJibeWalkNight = new JibeDisutility3(TransportMode.walk, ttWalk,false);
//
//
//        // Marginal Disutility maps
//        Map<Id<Link>,Double> bikeMarginalDisutilitiesDay = NetworkUtils2.precalculateLinkMarginalDisutilities(network,tdJibeBikeDay,0.,null,bike);
//        Map<Id<Link>,Double> bikeMarginalDisutilitiesNight = NetworkUtils2.precalculateLinkMarginalDisutilities(network,tdJibeBikeNight,0.,null,bike);
//
//        Map<Id<Link>,Double> walkMarginalDisutilitiesDay = NetworkUtils2.precalculateLinkMarginalDisutilities(network,tdJibeWalkDay,0.,null,null);
//        Map<Id<Link>,Double> walkMarginalDisutilitiesNight = NetworkUtils2.precalculateLinkMarginalDisutilities(network,tdJibeWalkNight,0.,null,null);

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
                log.warn("ERROR! Edge " + edgeID + " doesn't match its from and to nodes! Skipping this link...");
                continue;
            }

            // Length, travelTime, travelDisutility
            double length = link.getLength();
            double cycleTime = ttBike.getLinkTravelTime(link,0,null,bike);
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
            featureBuilder.add(link.getId().toString());
            featureBuilder.add(fwd);
            featureBuilder.add(length);
            featureBuilder.add(cycleTime);
            featureBuilder.add(walkTime);
            featureBuilder.add(link.getFreespeed());
//            featureBuilder.add(link.getAttributes().getAttribute("speedLimitMPH"));
//            featureBuilder.add(link.getAttributes().getAttribute("veh85percSpeedKPH"));
//            featureBuilder.add(bikeMarginalDisutilitiesDay.get(link.getId()));
//            featureBuilder.add(bikeMarginalDisutilitiesNight.get(link.getId()));
//            featureBuilder.add(walkMarginalDisutilitiesDay.get(link.getId()));
//            featureBuilder.add(walkMarginalDisutilitiesNight.get(link.getId()));
            featureBuilder.add(link.getAttributes().getAttribute("width"));
            featureBuilder.add((int) link.getNumberOfLanes());
            featureBuilder.add(link.getAttributes().getAttribute("aadt"));
            featureBuilder.add(link.getAttributes().getAttribute("aadtFwd"));
            featureBuilder.add(link.getAttributes().getAttribute("aadtFwd_car"));
            featureBuilder.add(link.getAttributes().getAttribute("aadtFwd_truck"));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.car));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.bike));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.walk));
            featureBuilder.add(link.getAttributes().getAttribute("motorway"));
            featureBuilder.add(link.getAttributes().getAttribute("trunk"));
            featureBuilder.add(link.getAttributes().getAttribute("dismount"));
//            featureBuilder.add(link.getAttributes().getAttribute("disconnected_"+ TransportMode.car));
//            featureBuilder.add(link.getAttributes().getAttribute("disconnected_"+ TransportMode.bike));
//            featureBuilder.add(link.getAttributes().getAttribute("disconnected_"+ TransportMode.walk));
            featureBuilder.add(Gradient.getGradient(link));
            featureBuilder.add(CycleProtection.getType(link).toString());
//            featureBuilder.add(link.getAttributes().getAttribute("endsAtJct"));
//            featureBuilder.add(link.getAttributes().getAttribute("crossVehicles"));
//            featureBuilder.add(Crossing.getType(link,"bike").toString());
//            featureBuilder.add(Crossing.getType(link,"walk").toString());
//            featureBuilder.add(link.getAttributes().getAttribute("crossLanes"));
//            featureBuilder.add(link.getAttributes().getAttribute("crossWidth"));
//            featureBuilder.add(link.getAttributes().getAttribute("crossAadt"));
//            featureBuilder.add(link.getAttributes().getAttribute("crossSpeedLimitMPH"));
//            featureBuilder.add(link.getAttributes().getAttribute("cross85PercSpeed"));
            featureBuilder.add(link.getAttributes().getAttribute("vgvi"));
            featureBuilder.add(link.getAttributes().getAttribute("shannon"));
            featureBuilder.add(link.getAttributes().getAttribute("POIs"));
            featureBuilder.add(link.getAttributes().getAttribute("negPOIs"));
//            featureBuilder.add(link.getAttributes().getAttribute("crime"));
//            featureBuilder.add(link.getAttributes().getAttribute("streetLights"));
//            featureBuilder.add(LinkAmbience.getVgviFactor(link));
//            featureBuilder.add(LinkAmbience.getLightingFactor(link));
//            featureBuilder.add(LinkAmbience.getShannonFactor(link));
//            featureBuilder.add(LinkAmbience.getCrimeFactor(link));
//            featureBuilder.add(LinkAmbience.getPoiFactor(link));
//            featureBuilder.add(LinkAmbience.getNegativePoiFactor(link));
            featureBuilder.add(link.getAttributes().getAttribute("hgvPOIs"));
//            featureBuilder.add(LinkAmbience.getDayAmbience(link));
//            featureBuilder.add(LinkAmbience.getNightAmbience(link));
            featureBuilder.add(link.getAllowedModes().contains(TransportMode.bike) ? LinkStressDiscrete.getCycleStress(link).toString() : "null");
            featureBuilder.add(LinkStress.getStress(link, TransportMode.bike));
            featureBuilder.add(JctStress.getStress(link,TransportMode.bike));
            featureBuilder.add(LinkStress.getStress(link,TransportMode.walk));
            featureBuilder.add(JctStress.getStress(link,TransportMode.walk));
            SimpleFeature feature = featureBuilder.buildFeature(null);
            collection.add(feature);
        }

        log.info(forwardLinks + " edges in the correct direction");
        log.info(backwardLinks + " edges in wrong direction needed to be reversed");

        // Write Geopackage
        File outputEdgesFile = new File(outputEdgesFilePath);
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

}
