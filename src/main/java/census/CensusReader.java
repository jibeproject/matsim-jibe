package census;

import gis.GisUtils;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import trip.Place;
import trads.TradsPurpose;
import trip.Trip;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class CensusReader {
    private static final Logger logger = Logger.getLogger(CensusReader.class);
    private static Integer commutersRead = 0;
    private static final String HOUSEHOLD_ID = "CENSUS";

    public static Set<Trip> readAndProcessMatrix(Map<String, Geometry> zones, String matrixFile, double scaleFactor) {

        Set<Trip> trips = new HashSet<>();
        String[] destinationZones;
        String originZone;
        String destinationZone;
        String line;
        String mode;

        try (BufferedReader ODMatrixFile = new BufferedReader(new FileReader(matrixFile))) {

            // Skip first 6 lines
            for (int i = 0 ; i < 6 ; i++) {
                ODMatrixFile.readLine();
            }

            // Read mode
            line = ODMatrixFile.readLine();
            mode = getMode(line.split(",")[1].replaceAll("\"",""));

            // Skip nex 2 lines
            for (int i = 0 ; i < 2 ; i++) {
                ODMatrixFile.readLine();
            }

            // Read destination zone names
            line = ODMatrixFile.readLine();
            destinationZones = Arrays.stream(line.split(",")).map(s -> s.substring(1).split(" : ")[0]).toArray(String[]::new);

            // Skip another line
            ODMatrixFile.readLine();

            // Read Matrix Flows
            while(!(line = ODMatrixFile.readLine()).isEmpty()) {
                String[] lineArray = line.split(",");
                originZone = lineArray[0].substring(1).split(" : ")[0];

                for (int i = 1 ; i < lineArray.length ; i++) {
                    destinationZone = destinationZones[i];
                    int count = Integer.parseInt(lineArray[i]);
                    if(count > 0) {
                        // Specify the ID of these two MSOAs
                        Geometry home = zones.get(originZone);
                        Geometry work = zones.get(destinationZone);
                        // Randomly creating the home and work location of each commuter
                        for (int j = 0; j < count; j++) {
                            if(Math.random() <= scaleFactor) {
                                commutersRead++;
                                // Specify the home location randomly
                                Coord homeCoord = GisUtils.drawRandomPointFromGeometry(home);
                                // Specify the working location randomly
                                Coord workCoord = GisUtils.drawRandomPointFromGeometry(work);

                                // Store zone for each commuter
                                Map<Place,String> MSOAs = new HashMap<>(2);
                                MSOAs.put(Place.HOME,originZone);
                                MSOAs.put(Place.DESTINATION,destinationZone);

                                // Store coords for each commuter
                                Map<Place,Coord> coords = new HashMap<>(2);
                                coords.put(Place.HOME,homeCoord);
                                coords.put(Place.DESTINATION,workCoord);

                                // Coords in boundary (always true)
                                Map<Place,Boolean> coordsInBoundary = new HashMap<>(2);
                                coordsInBoundary.put(Place.HOME,true);
                                coordsInBoundary.put(Place.DESTINATION,true);

                                trips.add(new Trip(HOUSEHOLD_ID,commutersRead,0,0,mode,
                                        TradsPurpose.HOME,TradsPurpose.WORK, MSOAs, coords,coordsInBoundary));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Read " + trips.size() + " census trips.");
        return trips;
    }

    private static String getMode(String censusMode) {
        switch (censusMode) {
            case "On foot":
                return TransportMode.walk;
            case "Bicycle":
                return TransportMode.bike;
            case "Driving a car or van":
                return TransportMode.car;
            default: throw new RuntimeException("Mode \"" + censusMode + "\" not recognised!");
        }
    }
}
