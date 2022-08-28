package trads;

import data.Place;
import org.matsim.api.core.v01.Coord;

import java.util.LinkedHashMap;
import java.util.Map;

public class TradsTrip {

    private final String householdId;
    private final int personId;
    private final int tripId;

    private final int startTime;

    private final Map<Place,Coord> coords;
    private final Map<Place,Boolean> coordsInsideBoundary;

    private final Map<String, Map<String,Object>> routeAttributes = new LinkedHashMap<>();

    public TradsTrip(String householdId, int personId, int tripId, int startTime,
                     Map<Place,Coord> coords, Map<Place,Boolean> coordsInsideBoundary) {
        this.householdId = householdId;
        this.personId = personId;
        this.tripId = tripId;
        this.startTime = startTime;
        this.coords = coords;
        this.coordsInsideBoundary = coordsInsideBoundary;
    }

   public Boolean isWithinBoundary(Place place) { return coordsInsideBoundary.get(place); }

    public Boolean match(Place a, Place b) {
        if(coords.get(a) != null && coords.get(b) != null) {
            return coords.get(a).equals(coords.get(b));
        } else {
            return null;
        }
    }

    public boolean routable(Place a, Place b) {
        if(coords.get(a) != null && coords.get(b) != null) {
            return coordsInsideBoundary.get(a) && coordsInsideBoundary.get(b) && !coords.get(a).equals(coords.get(b));
        } else {
            return false;
        }
    }

    public int getStartTime() { return startTime; }

    public Coord getCoord(Place place) { return coords.get(place); }

    public void setAttributes(String route, Map<String,Object> attributes) {
        routeAttributes.put(route,attributes);
    }

    public String getHouseholdId() {
        return householdId;
    }

    public int getPersonId() {
        return personId;
    }

    public int getTripId() {
        return tripId;
    }

    public Object getAttribute(String route, String attr) {
        if(routeAttributes.get(route) != null) {
            return routeAttributes.get(route).get(attr);
        } else return null;
    }
    
}
