package trads;

import org.matsim.api.core.v01.Coord;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TradsTrip {

    private final String householdId;
    private final int personId;
    private final int tripId;

    private final int startTime;
    
    private final Coord cOrig;
    private final Coord cDest;

    private Map<String, Double> costsByMode = new HashMap<>();
    private Map<String, Double> timesByMode = new HashMap<>();

    private Map<String, Double> distancesByMode = new HashMap<>();
    private Map<String, Map<String,Object>> attributesByMode = new LinkedHashMap<>();


    private Boolean originWithinBoundary;
    private Boolean destinationWithinBoundary;

    private Boolean sameOriginAndDestination;

    public TradsTrip(String householdId, int personId, int tripId, int startTime, Coord cOrig, Coord cDest,
                     Boolean originWithinBoundary, Boolean destinationWithinBoundary, Boolean sameOriginAndDestination) {
        this.householdId = householdId;
        this.personId = personId;
        this.tripId = tripId;
        this.startTime = startTime;
        this.cOrig = cOrig;
        this.cDest = cDest;
        this.originWithinBoundary = originWithinBoundary;
        this.destinationWithinBoundary = destinationWithinBoundary;
        this.sameOriginAndDestination = sameOriginAndDestination;
    }

    public Boolean isOriginWithinBoundary() {
        return originWithinBoundary;
    }

    public Boolean isDestinationWithinBoundary() {
        return destinationWithinBoundary;
    }

    public Boolean originMatchesDestination() {
        return sameOriginAndDestination;
    }

    public boolean isTripWithinBoundary() {
        if(originWithinBoundary != null && destinationWithinBoundary != null) {
            return originWithinBoundary && destinationWithinBoundary;
        }
        else return false;
    }

    public int getStartTime() { return startTime; }

    public Coord getOrigCoord() {
        return cOrig;
    }
    
    public Coord getDestCoord() {
        return cDest;
    }

    public void setCost(String mode, Double cost) {
        costsByMode.put(mode,cost);
    }

    public void setTime(String mode, Double time) {
        timesByMode.put(mode,time);
    }

    public void setDist(String mode, Double distance) {
        distancesByMode.put(mode,distance);
    }

    public void setAttributes(String mode, Map<String,Object> attributes) {
        attributesByMode.put(mode,attributes);
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

    public double getCost(String mode) {
        return costsByMode.get(mode);
    }

    public double getTime(String mode) {
        return timesByMode.get(mode);
    }

    public double getDistance(String mode) { return distancesByMode.get(mode); }

    public Object getAttribute(String mode, String attr) { return attributesByMode.get(mode).get(attr); }
    
}
