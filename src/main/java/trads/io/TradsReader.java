package trads.io;

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
import trads.TradsPurpose;
import trip.Trip;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static trads.TradsPurpose.*;
import static trads.io.TradsAttributes.*;

public class TradsReader {

    private final static Logger logger = Logger.getLogger(TradsReader.class);

    public static Set<Trip> readTrips(Geometry geometry) throws IOException {
        Set<Trip> trips = new LinkedHashSet<>();
        String recString;
        Counter counter = new Counter("Processed " + " TRADS records.");
        int badCoords = 0;
        int badTimes = 0;

        // Open Reader
        String filePath = Resources.instance.getString(Properties.TRADS_TRIPS);
        if(filePath == null) {
            throw new RuntimeException("No TRADS survey path in the properties file!");
        }
        BufferedReader in = new BufferedReader(new FileReader(filePath));

        GeometryFactory gf = new GeometryFactory();

        // Read Header
        recString = in.readLine();
        String[] header = recString.split(SEP);
        int posHouseholdId = findPositionInArray(HOUSEHOLD_ID, header);
        int posPersonId = findPositionInArray(PERSON_ID, header);
        int posTripId = findPositionInArray(TRIP_ID, header);
        int posStartTime = findPositionInArray(START_TIME, header);
        int posMainMode = findPositionInArray(MAIN_MODE, header);
        int posStartPurpose = findPositionInArray(START_PURPOSE, header);
        int posEndPurpose = findPositionInArray(END_PURPOSE, header);
        int posHomeZone = findPositionInArray(HOME_ZONE,header);
        int posOriginZone = findPositionInArray(ORIGIN_ZONE,header);
        int posDestinationZone = findPositionInArray(DESTINATION_ZONE,header);
        int posHomeX = findPositionInArray(X_HOME_COORD, header);
        int posHomeY = findPositionInArray(Y_HOME_COORD, header);
        int posOrigX = findPositionInArray(X_ORIGIN_COORD, header);
        int posOrigY = findPositionInArray(Y_ORIGIN_COORD, header);
        int posDestX = findPositionInArray(X_DESTINATION_COORD, header);
        int posDestY = findPositionInArray(Y_DESTINATION_COORD, header);

        while ((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] lineElements = recString.split(SEP);

            String householdId = lineElements[posHouseholdId];
            int personId = Integer.parseInt(lineElements[posPersonId]);
            int tripId = Integer.parseInt(lineElements[posTripId]);

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
            TradsPurpose startPurpose = getPurpose(lineElements[posStartPurpose]);
            TradsPurpose endPurpose = getPurpose(lineElements[posEndPurpose]);

            // Zones
            Map<Place,String> zones = new HashMap<>(3);
            zones.put(Place.HOME,lineElements[posHomeZone]);
            zones.put(Place.ORIGIN,lineElements[posOriginZone]);
            zones.put(Place.DESTINATION,lineElements[posDestinationZone]);

            // COORDS
            Map<Place, Coord> coords = new HashMap<>(3);
            Map<Place, Boolean> coordsInBoundary = new HashMap<>(3);

            // Read household coord
            try {
                double x = Double.parseDouble(lineElements[posHomeX]);
                double y = Double.parseDouble(lineElements[posHomeY]);
                coords.put(Place.HOME, CoordUtils.createCoord(x,y));
                coordsInBoundary.put(Place.HOME,geometry.contains(gf.createPoint(new Coordinate(x,y))));
            } catch (NumberFormatException e) {
                logger.warn("Unreadable HOME coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                badCoords++;
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
            try {
                double x = Double.parseDouble(lineElements[posDestX]);
                double y = Double.parseDouble(lineElements[posDestY]);
                coords.put(Place.DESTINATION, CoordUtils.createCoord(x,y));
                coordsInBoundary.put(Place.DESTINATION,geometry.contains(gf.createPoint(new Coordinate(x,y))));
            } catch (NumberFormatException e) {
                logger.warn("Unreadable DESTINATION coordinates for household " + householdId + ", person " + personId + ", trip " + tripId);
                badCoords++;
            }

            trips.add(new Trip(householdId, personId, tripId, startTime, mainMode, startPurpose, endPurpose, zones, coords, coordsInBoundary));
        }
        in.close();

        logger.info(counter.getCounter() + " trips processed.");
        logger.info(badTimes + " trips with double-value start times.");
        logger.info(badCoords + " unreadable coordinates.");

        return trips;
    }

    private static int findPositionInArray (String string, String[] array) {
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

    private static String getTransportMode(String tradsMode) {
        switch(tradsMode) {
            case "Walk": return TransportMode.walk;
            case "Bicycle": return TransportMode.bike;
            case "Motorcycle, scooter, moped": return TransportMode.motorcycle;
            case "Car or van driver": return TransportMode.car;
            case "Train":
            case "2+ Train":
                return TransportMode.train;
            case "Taxi, minicab": return TransportMode.taxi;
            default: return tradsMode;
        }
    }

    private static TradsPurpose getPurpose(String purpose) {
        switch(purpose) {
            case "Home": return HOME;
            case "Usual place of work": return WORK;
            case "Education as pupil, student": return EDUCATION;
            case "Visit friends or relatives": return VISIT_FRIENDS_OR_FAMILY;
            case "Shopping Food": return SHOPPING_FOOD;
            case "Shopping Non food": return SHOPPING_NON_FOOD;
            case "Escorting to place of work, pick-up, drop-off": return ESCORT_WORK;
            case "Escorting to place of education, pick-up, drop-off": return ESCORT_EDUCATION;
            case "Childcare  taking or collecting child to or from babysitter, nursery etc": return ESCORT_CHILDCARE;
            case "Accompanying or giving lift to other person, not school, or work": return ESCORT_OTHER;
            case "Use Services, Personal Business, bank, hairdresser, library etc": return PERSONAL_BUSINESS;
            case "Health or medical visit": return MEDICAL;
            case "Social - Entertainment, recreation, Participate in sport, pub, restaurant": return SOCIAL;
            case "Work - Business, other": return BUSINESS_TRIP;
            case "Moving people or goods in connection with employment": return BUSINESS_TRANSPORT;
            case "Worship or religious observance": return WORSHIP;
            case "Round trip walk, cycle, drive for enjoyment": return RECREATIONAL_ROUND_TRIP;
            case "Unpaid, voluntary work": return VOLUNTEERING;
            case "Tourism, sightseeing": return TOURISM;
            case "Staying at hotel or other temporary accommodation": return TEMPORARY_ACCOMMODATION;
            case "Other": return OTHER;
            case "NR": return NO_RESPONSE;
            default: throw new RuntimeException("Purpose " + purpose + " not accounted for! Please update list of purposes");
        }
    }
}
