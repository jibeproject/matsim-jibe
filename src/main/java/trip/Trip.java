package trip;

import org.matsim.api.core.v01.Coord;
import routing.graph.TreeNode;
import trads.TradsPurpose;

import java.util.*;

public class Trip {

    private final String householdId;
    private final int personId;
    private final int tripId;
    private final int startTime;
    private final String mainMode;
    private final TradsPurpose startPurpose;
    private final TradsPurpose endPurpose;
    private final Map<Place,String> zones;
    private final Map<Place,Coord> coords;
    private final Map<Place,Boolean> coordsInsideBoundary;
    private final Map<String, Map<String,Object>> routeAttributes = new LinkedHashMap<>();
    private final Map<String,Integer> routePathIndices = new LinkedHashMap<>();
    private final List<Route> routes = new ArrayList<>();
    private Set<TreeNode> paths;

    public Trip(String householdId, int personId, int tripId, int startTime,
                String mainMode, TradsPurpose startPurpose, TradsPurpose endPurpose, Map<Place, String> zones, Map<Place,Coord> coords, Map<Place,Boolean> coordsInsideBoundary) {
        this.householdId = householdId;
        this.personId = personId;
        this.tripId = tripId;
        this.startTime = startTime;
        this.mainMode = mainMode;
        this.startPurpose = startPurpose;
        this.endPurpose = endPurpose;
        this.zones = zones;
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

    public String getZone(Place place) { return zones.get(place); }

    public Coord getCoord(Place place) { return coords.get(place); }

    public void setAttributes(String route, Map<String,Object> attributes) {
        routeAttributes.put(route,attributes);
    }

    public void setRoutePath(String route, Coord startCoord, int[] edgeIDs, double distance, double time) {

        Integer pathKey = findPathKey(routes,edgeIDs,startCoord,distance,time);
        if(pathKey == null) {
            pathKey = routes.size();
            routes.add(new Route(startCoord, edgeIDs,distance,time));
        }
        routePathIndices.put(route,pathKey);
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

    public String getMainMode() { return mainMode; }

    public TradsPurpose getStartPurpose() { return startPurpose; }
    public TradsPurpose getEndPurpose() { return endPurpose; }

    public Coord getStartCoord(String route) {
        return routes.get(routePathIndices.get(route)).getStartCoord();
    }

    public List<Route> getUniqueRoutes() {
        return routes;
    }

    public int getPathIndex(String route) {
        return routePathIndices.get(route);
    }
    public Map<String,int[]> getAllRoutePaths() {
        Map<String,int[]> result = new LinkedHashMap<>();

        for(Map.Entry<String,Integer> e : routePathIndices.entrySet()) {
            result.put(e.getKey(), routes.get(e.getValue()).getLinks());
        }

        return result;
    }

    public Object getAttribute(String route, String attr) {
        if(routeAttributes.get(route) != null) {
            return routeAttributes.get(route).get(attr);
        } else return null;
    }

    public Set<String> getRouteNames() {
        return routeAttributes.keySet();
    }

    private static Integer findPathKey(List<Route> routes, int[] newPath, Coord startCoord, double distance, double time) {
        for(int i = 0 ; i < routes.size() ; i++) {
            Route route = routes.get(i);
            if (Arrays.equals(route.getLinks(),newPath)) {
                if(!(startCoord.equals(route.getStartCoord()) && distance == route.getDistance() && time == route.getTime())) {
                    throw new RuntimeException("Matching route but mismatching startCoord/distance/time.\n" +
                            "This should not happen! You need to debug!");
                }
                return i;
            }
        }
        return null;
    }

    public Set<TreeNode> getPaths() {
        return paths;
    }

    public void setPaths(Set<TreeNode> paths) {
        this.paths = paths;
    }
}
