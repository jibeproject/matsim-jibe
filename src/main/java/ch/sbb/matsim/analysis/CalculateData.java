/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import ch.sbb.matsim.analysis.calc.GeometryCalculator;
import ch.sbb.matsim.analysis.calc.IndicatorCalculator;
import ch.sbb.matsim.analysis.calc.PtCalculator;
import ch.sbb.matsim.analysis.data.GeometryData;
import ch.sbb.matsim.analysis.data.IndicatorData;
import ch.sbb.matsim.analysis.data.PtData;
import ch.sbb.matsim.analysis.io.GeometryWriter;
import ch.sbb.matsim.analysis.io.IndicatorWriter;
import ch.sbb.matsim.analysis.io.PtWriter;
import ch.sbb.matsim.analysis.matrix.FloatMatrix;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import network.NetworkUtils2;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.SpatialIndex;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.StringUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import routing.TravelAttribute;

/**
 * Main class to calculate skim matrices.
 * Provides a main-method to be directly started from the command line, but the
 * main-method also acts as a template for custom code using the skims calculation.
 *
 * All calculated matrices are written to files with fixed names (see constants in this class)
 * in an output directory.
 *
 * @author mrieser / SBB
 */
public class CalculateData {

    private static final Logger log = Logger.getLogger(CalculateData.class);

    private final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final String outputDirectory;
    private final int numberOfThreads;
    private Integer batchSize;
    private Map<String, Coord> zoneCoordMap = null;

    public CalculateData(String outputDirectory, int numberOfThreads, Integer batchSize) {
        this.outputDirectory = outputDirectory;
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            log.info("create output directory " + outputDirectory);
            outputDir.mkdirs();
        } else {
            log.warn("output directory exists already, might overwrite data. " + outputDirectory);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException("User does not want to overwrite data.");
            }
        }

        this.numberOfThreads = numberOfThreads;
        this.batchSize = batchSize;
    }

    public void writeSamplingPointsToFile(File file) throws IOException {
        log.info("write chosen coordinates to file " + file.getAbsolutePath());
        try (BufferedWriter writer = IOUtils.getBufferedWriter(file.getAbsolutePath())) {
            writer.write("ZONE;POINT_INDEX;X;Y\n");
            for (Map.Entry<String, Coord> e : this.zoneCoordMap.entrySet()) {
                String zoneId = e.getKey();
                Coord coord = e.getValue();
                writer.write(zoneId); writer.write(";0;");
                writer.write(Double.toString(coord.getX())); writer.write(";");
                writer.write(Double.toString(coord.getY())); writer.write("\n");
            }
        }
    }

    public final void loadSamplingPointsFromFile(String filename) throws IOException {
        log.info("loading sampling points from " + filename);
        this.zoneCoordMap = buildZoneCoordMap(filename);
        if (batchSize == null) {
            batchSize = zoneCoordMap.size();
        }
    }

    public final void calculateRouteIndicators(String networkFilename, Config config, TravelTime tt, TravelDisutility td,
                                               String outputPrefix, TravelAttribute[] aggregatedAttributes,
                                               String transportMode, Predicate<Link> xy2linksPredicate) throws IOException {
        String prefix = outputPrefix == null ? "" : outputPrefix;
        Scenario scenario = ScenarioUtils.createScenario(config);
        log.info("loading network from " + networkFilename);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);

        log.info("extracting mode-specific network for " + transportMode);
        final Network modeSpecificNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(modeSpecificNetwork, Collections.singleton(transportMode));
        //new NetworkWriter(modeSpecificNetwork).write(outputDirectory + "filteredNetwork.xml");

        log.info("filter mode-specific network for assigning links to locations");
        final Network xy2linksNetwork = NetworkUtils2.extractXy2LinksNetwork(modeSpecificNetwork, xy2linksPredicate);

        log.info("calculating zone-node map");
        Map<String, Node> zoneNodeMap = buildZoneNodeMap(zoneCoordMap, xy2linksNetwork, modeSpecificNetwork);

        log.info("splitting into batches of size " + batchSize);


        Iterable<List<String>> batches = Iterables.partition(zoneCoordMap.keySet(), batchSize);
        long numberOfBatches = StreamSupport.stream(batches.spliterator(), false).count();
        log.info("created " + numberOfBatches + " batches");

        int counter = 0;
        Set<String> destinations = zoneCoordMap.keySet();
        for(List<String> originBatch : batches) {
            counter++;
            Set<String> origins = Sets.newHashSet(originBatch);
            log.info("INITIATING BATCH " + counter + " OF " + numberOfBatches);
            long startTime = System.currentTimeMillis();
            IndicatorData<String> netIndicators = IndicatorCalculator.calculate(
                    modeSpecificNetwork, origins, destinations, zoneNodeMap, tt, td, null,null, this.numberOfThreads);
            long endTime = System.currentTimeMillis();
            log.info("Batch " + counter + " calculation time: " + (endTime - startTime));

            log.info("Writing batch " + counter + " data to " + outputDirectory + (prefix.isEmpty() ? "" : (" with prefix " + prefix)));
            startTime = System.currentTimeMillis();
            IndicatorWriter.writeAsCsv(netIndicators,outputDirectory + "/" + prefix + "batch_" + counter + ".csv.gz");
            endTime = System.currentTimeMillis();
            log.info("Batch " + counter + " writing time: " + (endTime - startTime));
            // break; // break for debugging only
        }
    }

    public final void calculateRouteGeometries(String networkFilename,Config config, String[] originZoneNames,
                                               TravelTime tt, TravelDisutility td, String inputEdgesGpkg,
                                               String outputFilePath, String transportMode,
                                               Predicate<Link> xy2linksPredicate) throws IOException, FactoryException {

        Scenario scenario = ScenarioUtils.createScenario(config);
        log.info("loading network from " + networkFilename);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);

        log.info("extracting mode-specific network for " + transportMode);
        final Network modeSpecificNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(modeSpecificNetwork, Collections.singleton(transportMode));
        //new NetworkWriter(modeSpecificNetwork).write(outputDirectory + "filteredNetwork.xml");

        log.info("filter mode-specific network for assigning links to locations");
        final Network xy2linksNetwork = NetworkUtils2.extractXy2LinksNetwork(modeSpecificNetwork, xy2linksPredicate);

        log.info("calculating zone-node map");
        Map<String, Node> zoneNodeMap = buildZoneNodeMap(zoneCoordMap, xy2linksNetwork, modeSpecificNetwork);

        log.info("fixing origin zones");
        Set<String> originZones;
        if(originZoneNames == null) {
            originZones = zoneCoordMap.keySet();
        } else {
            originZones = Set.of(originZoneNames);
        }

        log.info("splitting into batches of size " + batchSize);
        Iterable<List<String>> batches = Iterables.partition(originZones, batchSize);
        long numberOfBatches = StreamSupport.stream(batches.spliterator(), false).count();
        log.info("created " + numberOfBatches + " batches");

        int counter = 0;
        Set<String> destinations = zoneCoordMap.keySet();
        for(List<String> originBatch : batches) {
            counter++;
            Set<String> origins = Sets.newHashSet(originBatch);
            log.info("INITIATING BATCH " + counter + " OF " + numberOfBatches);
            long startTime = System.currentTimeMillis();
            GeometryData<String> geometries = GeometryCalculator.calculate(
                    modeSpecificNetwork, origins, destinations, zoneNodeMap, tt, td, null,null, this.numberOfThreads);
            long endTime = System.currentTimeMillis();
            log.info("Batch " + counter + " calculation time: " + (endTime - startTime));

            startTime = System.currentTimeMillis();
            GeometryWriter.writeGpkg(geometries, zoneNodeMap, inputEdgesGpkg, outputFilePath);
            endTime = System.currentTimeMillis();
            log.info("Batch " + counter + " writing time: " + (endTime - startTime));

            // break; // break for debugging only
        }
    }

    public final void calculatePtIndicators(String networkFilename, String transitScheduleFilename, double startTime, double endTime, Config config, String outputPrefix, BiPredicate<TransitLine, TransitRoute> trainDetector) throws IOException {
        String prefix = outputPrefix == null ? "" : outputPrefix;
        Scenario scenario = ScenarioUtils.createScenario(config);
        log.info("loading schedule from " + transitScheduleFilename);
        new TransitScheduleReader(scenario).readFile(transitScheduleFilename);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);

        log.info("prepare PT Matrix calculation");
        RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(config);
        raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
        SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), scenario.getTransitVehicles(), raptorConfig, scenario.getNetwork(), null);
        RaptorParameters raptorParameters = RaptorUtils.createParameters(config);

        log.info("splitting into batches of size " + batchSize);
        Iterable<List<String>> batches = Iterables.partition(zoneCoordMap.keySet(), batchSize);
        long numberOfBatches = StreamSupport.stream(batches.spliterator(), false).count();
        log.info("created " + numberOfBatches + " batches");

        int counter = 0;
        Set<String> destinations = zoneCoordMap.keySet();
        for(List<String> originBatch : batches) {
            counter++;
            Set<String> origins = Sets.newHashSet(originBatch);
            log.info("BATCH " + counter + ": calc PT matrices for " + Time.writeTime(startTime) + " - " + Time.writeTime(endTime));
            PtData<String> matrices = PtCalculator.calculatePtIndicators(
                    raptorData, origins, destinations, this.zoneCoordMap, startTime, endTime, 120, raptorParameters, this.numberOfThreads, trainDetector);

            log.info("BATCH " + counter + ": write PT matrices to " + outputDirectory + (prefix.isEmpty() ? "" : (" with prefix " + prefix)));
            PtWriter.writeAsCsv(matrices,outputDirectory + "/" + prefix + "_" + "batch" + counter + ".csv.gz");
            // break; // for debugging only
        }
    }

    private static <T> void combineMatrices(FloatMatrix<T> matrix1, FloatMatrix<T> matrix2) {
        Set<T> ids = matrix2.orig2index.keySet();
        for (T fromId : ids) {
            for (T toId : ids) {
                float value2 = matrix2.get(fromId, toId);
                matrix1.add(fromId, toId, value2);
            }
        }
    }

    private String findZone(Coord coord, SpatialIndex zonesQt, String zonesIdAttributeName) {
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(coord.getX(), coord.getY()));
        List elements = zonesQt.query(pt.getEnvelopeInternal());
        for (Object o : elements) {
            SimpleFeature z = (SimpleFeature) o;
            if (((Geometry) z.getDefaultGeometry()).intersects(pt)) {
                return z.getAttribute(zonesIdAttributeName).toString();
            }
        }
        return null;
    }

    public static Map<String, Coord> buildZoneCoordMap(String filename) throws IOException {
        String expectedHeader = "ZONE;POINT_INDEX;X;Y";
        Map<String, Coord> zoneCoordMap = new LinkedHashMap<>();
        try (BufferedReader reader = IOUtils.getBufferedReader(filename)) {
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new RuntimeException("Bad header, expected '" + expectedHeader + "', got: '" + header + "'.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = StringUtils.explode(line, ';');
                String zoneId = parts[0];
                double x = Double.parseDouble(parts[2]);
                double y = Double.parseDouble(parts[3]);
                Coord coord = new Coord(x, y);
                zoneCoordMap.put(zoneId,coord);
            }
        }
        return zoneCoordMap;
    }

    public static <T> Map<T, Node> buildZoneNodeMap(Map<T, Coord> zoneCoordMap, Network xy2lNetwork, Network routingNetwork) {
        Map<T, Node> zoneNodeMap = new HashMap<>();
        for (Map.Entry<T, Coord> e : zoneCoordMap.entrySet()) {
            T zoneId = e.getKey();
            Coord coord = e.getValue();
            Node node = routingNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2lNetwork, coord).getToNode().getId());
            zoneNodeMap.put(zoneId, node);
        }
        return zoneNodeMap;
    }

    private static class WeightedCoord {
        Coord coord;
        double weight;

        private WeightedCoord(Coord coord, double weight) {
            this.coord = coord;
            this.weight = weight;
        }
    }
}
