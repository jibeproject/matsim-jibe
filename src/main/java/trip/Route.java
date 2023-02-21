package trip;

import org.matsim.api.core.v01.Coord;

public class Route {
    private final Coord startCoord;
    private final int[] path;
    private final double distance;
    private final double time;

    public Route(Coord startCoord, int[] path, double distance, double time) {
        this.startCoord = startCoord;
        this.path = path;
        this.distance = distance;
        this.time = time;
    }

    public Coord getStartCoord() {
        return startCoord;
    }

    public int[] getLinks() {
        return this.path;
    }

    public double getDistance() {
        return this.distance;
    }

    public double getTime() {
        return time;
    }
}
