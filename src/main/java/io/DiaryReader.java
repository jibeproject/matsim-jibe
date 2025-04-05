package io;

import trip.Place;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import resources.Properties;
import resources.Resources;
import trip.Purpose;
import trip.Trip;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static trip.Purpose.*;

public class DiaryReader {

    private final static Logger logger = Logger.getLogger(DiaryReader.class);

    public static Set<Trip> readTrips(Geometry geometry) throws IOException {
        return readTrips(geometry,null);
    }

    public static Set<Trip> readTrips(Geometry geometry, DiaryRecordTester tester) throws IOException {
        Set<Trip> trips = new LinkedHashSet<>();
        String recString;
        Counter counter = new Counter("Processed " + "travel diary records.");
        int badCoords = 0;
        int badTimes = 0;

        // Open Reader
        String filePath = Resources.instance.getString(Properties.DIARY_FILE);
        if(filePath == null) {
            throw new RuntimeException("No TRADS survey path in the properties file!");
        }
        BufferedReader in = new BufferedReader(new FileReader(filePath));

        GeometryFactory gf = new GeometryFactory();

        // Read Header
        recString = in.readLine();
        String[] header = recString.split(Resources.instance.getString(Properties.DIARY_DELIMITER));
        int posHouseholdId = findPositionInArray(Properties.HOUSEHOLD_ID, header);
        int posPersonId = findPositionInArray(Properties.PERSON_ID, header);
        int posTripId = findPositionInArray(Properties.TRIP_ID, header);
        int posStartTime = findPositionInArray(Properties.START_TIME, header);
        int posMainMode = findPositionInArray(Properties.MAIN_MODE, header);
        int posStartPurpose = findPositionInArray(Properties.ORIGIN_PURPOSE, header);
        int posEndPurpose = findPositionInArray(Properties.DESTINATION_PURPOSE, header);
        int posHomeZone = findPositionInArray(Properties.HOME_ZONE,header);
        int posMainZone = findPositionInArray(Properties.MAIN_ZONE,header);
        int posOriginZone = findPositionInArray(Properties.ORIGIN_ZONE,header);
        int posDestinationZone = findPositionInArray(Properties.DESTINATION_ZONE,header);
        int posHomeX = findPositionInArray(Properties.HOME_X, header);
        int posHomeY = findPositionInArray(Properties.HOME_Y, header);
        int posMainX = findPositionInArray(Properties.MAIN_X, header);
        int posMainY = findPositionInArray(Properties.MAIN_Y, header);
        int posOrigX = findPositionInArray(Properties.ORIGIN_X, header);
        int posOrigY = findPositionInArray(Properties.ORIGIN_Y, header);
        int posDestX = findPositionInArray(Properties.DESTINATION_X, header);
        int posDestY = findPositionInArray(Properties.DESTINATION_Y, header);


        String sep = Resources.instance.getString(Properties.DIARY_DELIMITER);
        while ((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] lineElements = recString.split(sep);

            String householdId = lineElements[posHouseholdId];
            int personId = Integer.parseInt(lineElements[posPersonId]);
            int tripId = Integer.parseInt(lineElements[posTripId]);

            if(tester != null) {
                if(!tester.test(householdId,personId,tripId)) {
                    continue;
                }
            }

            // Read start time if available, otherwise assume 8am
            int startTime;
            if(posStartTime != -1) {
                try {
                    startTime = Integer.parseInt(lineElements[posStartTime]);
                } catch (NumberFormatException e) {
                    startTime = (int) Double.parseDouble(lineElements[posStartTime]);
                    logger.warn("Unusual start time " + lineElements[posStartTime] + " for household " + householdId + ", person " + personId + ", trip " + tripId +
                            ". Set to " + startTime);
                    badTimes++;
                }
            } else startTime = 28800;

            // Read main mode
            String mainMode = getTransportMode(lineElements[posMainMode]);

            // Read start and end purpose
            Purpose startPurpose = null;
            Purpose endPurpose = null;

            if(posStartPurpose != -1 && posEndPurpose != -1) {
                startPurpose = getPurpose(lineElements[posStartPurpose]);
                endPurpose = getPurpose(lineElements[posEndPurpose]);
            }

            // Zones
            Map<Place,String> zones = new HashMap<>(3);
            if(posHomeZone != -1) zones.put(Place.HOME,lineElements[posHomeZone]);
            if(posMainZone != -1) zones.put(Place.MAIN,lineElements[posMainZone]);
            if(posOriginZone != -1) zones.put(Place.ORIGIN,lineElements[posOriginZone]);
            if(posDestinationZone != -1) zones.put(Place.DESTINATION,lineElements[posDestinationZone]);

            // COORDS
            Map<Place, Coord> coords = new HashMap<>(3);
            Map<Place, Boolean> coordsInBoundary = new HashMap<>(3);

            // Read household coord
            if(posHomeX != -1 && posHomeY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posHomeX]);
                    double y = Double.parseDouble(lineElements[posHomeY]);
                    coords.put(Place.HOME, CoordUtils.createCoord(x, y));
                    coordsInBoundary.put(Place.HOME, geometry.contains(gf.createPoint(new Coordinate(x, y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable HOME coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            // Read main coord
            if(posMainX != -1 && posMainY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posMainX]);
                    double y = Double.parseDouble(lineElements[posMainY]);
                    coords.put(Place.MAIN, CoordUtils.createCoord(x,y));
                    coordsInBoundary.put(Place.MAIN,geometry.contains(gf.createPoint(new Coordinate(x,y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable MAIN coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            // Read origin coord
            if(posOrigX != -1 && posOrigY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posOrigX]);
                    double y = Double.parseDouble(lineElements[posOrigY]);
                    coords.put(Place.ORIGIN, CoordUtils.createCoord(x,y));
                    coordsInBoundary.put(Place.ORIGIN, geometry.contains(gf.createPoint(new Coordinate(x,y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable ORIGIN coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            // Read destination coord
            if(posDestX != -1 && posDestY != -1) {
                try {
                    double x = Double.parseDouble(lineElements[posDestX]);
                    double y = Double.parseDouble(lineElements[posDestY]);
                    coords.put(Place.DESTINATION, CoordUtils.createCoord(x, y));
                    coordsInBoundary.put(Place.DESTINATION, geometry.contains(gf.createPoint(new Coordinate(x, y))));
                } catch (NumberFormatException e) {
                    logger.warn("Unreadable DESTINATION coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                    badCoords++;
                }
            }

            trips.add(new Trip(householdId, personId, tripId, startTime, mainMode, startPurpose, endPurpose, zones, coords, coordsInBoundary));
        }
        in.close();

        logger.info(counter.getCounter() + " trips processed.");
        logger.info(badTimes + " trips with double-value start times.");
        logger.info(badCoords + " unreadable coordinates.");

        return trips;
    }

    public interface DiaryRecordTester {

        boolean test(String hhid, int persid, int tripid);

    }

    private static int findPositionInArray (String property, String[] array) {
        String string = Resources.instance.getString(property);
        if (string == null) {
            logger.warn("No diary attribute for \"" + property + "\" specified in properties file");
            return -1;
        } else {
            int ind = -1;
            for (int a = 0; a < array.length; a++) {
                if (array[a].equalsIgnoreCase(string)) {
                    ind = a;
                }
            }
            if (ind == -1) {
                logger.error ("Could not find element " + string +
                        " in array");
            }
            return ind;
        }
    }

    private static String getTransportMode(String mode) {
        return switch (mode) {
            case "Walk", "Walking" -> TransportMode.walk;
            case "Bicycle" -> TransportMode.bike;
            case "Motorcycle", "Motorcycle, scooter, moped" -> TransportMode.motorcycle;
            case "Car or van driver", "Vehicle Driver" -> TransportMode.car;
            case "Train", "2+ Train" -> TransportMode.train;
            case "Taxi", "Taxi, minicab" -> TransportMode.taxi;
            case "Public Bus", "School Bus", "Tram" -> TransportMode.pt;
            default -> TransportMode.other;
        };
    }

    // todo: generalise to also work for melbourne
    private static Purpose getPurpose(String purpose) {
        return switch (purpose) {
            case "Home" -> HOME;
            case "Usual place of work" -> WORK;
            case "Education as pupil, student" -> EDUCATION;
            case "Visit friends or relatives" -> VISIT_FRIENDS_OR_FAMILY;
            case "Shopping Food" -> SHOPPING_FOOD;
            case "Shopping Non food" -> SHOPPING_NON_FOOD;
            case "Escorting to place of work, pick-up, drop-off" -> ESCORT_WORK;
            case "Escorting to place of education, pick-up, drop-off" -> ESCORT_EDUCATION;
            case "Childcare  taking or collecting child to or from babysitter, nursery etc" -> ESCORT_CHILDCARE;
            case "Accompanying or giving lift to other person, not school, or work" -> ESCORT_OTHER;
            case "Use Services, Personal Business, bank, hairdresser, library etc" -> PERSONAL_BUSINESS;
            case "Health or medical visit" -> MEDICAL;
            case "Social - Entertainment, recreation, Participate in sport, pub, restaurant" -> SOCIAL;
            case "Work - Business, other" -> BUSINESS_TRIP;
            case "Moving people or goods in connection with employment" -> BUSINESS_TRANSPORT;
            case "Worship or religious observance" -> WORSHIP;
            case "Round trip walk, cycle, drive for enjoyment" -> RECREATIONAL_ROUND_TRIP;
            case "Unpaid, voluntary work" -> VOLUNTEERING;
            case "Tourism, sightseeing" -> TOURISM;
            case "Staying at hotel or other temporary accommodation" -> TEMPORARY_ACCOMMODATION;
            case "Other" -> OTHER;
            case "NR" -> NO_RESPONSE;
            default ->
                    throw new RuntimeException("Purpose " + purpose + " not accounted for! Please update list of purposes");
        };
    }
}
