package resources;

public class Properties {

    // GIS Files (must be in .gpkg format)
    public static final String REGION_BOUNDARY = "region.boundary";
    public static final String NETWORK_BOUNDARY = "network.boundary";
    public static final String NETWORK_LINKS = "network.links";
    public static final String NETWORK_NODES = "network.nodes";
    public static final String COORDINATE_SYSTEM = "coordinate.system";

    // MATSim data (must be in .xml format)
    public static final String MATSIM_ROAD_NETWORK = "matsim.road.network";
    public static final String MATSIM_TRANSIT_NETWORK = "matsim.transit.network";
    public static final String MATSIM_TRANSIT_SCHEDULE = "matsim.transit.schedule";
    
    // Other properties
    public static final String NUMBER_OF_THREADS = "number.of.threads";
    public static final String MAX_BIKE_SPEED = "max.bike.speed";
    public static final String DECAY_PERCENTILE = "decay.percentile";
    
    // TRADS Survey data
    public static final String TRADS_TRIPS = "trads.trips";


    // Parameters for JIBE routing (generic)
    public static final String TIME = "time";
    public static final String DISTANCE = "dist";
    public static final String GRADIENT = "gradient";
    public static final String COMFORT = "comfort";
    public static final String ATTRACTIVENESS = "attractiveness";
    public static final String LINK_STRESS = "stress.link";
    public static final String JUNCTION_STRESS = "stress.jct";
    
    // Parameters for walk JIBE routing
    private static final String MC_WALK = "mc.walk.";
    public static final String MC_WALK_TIME = MC_WALK + TIME;
    public static final String MC_WALK_DIST = MC_WALK + DISTANCE;
    public static final String MC_WALK_GRADIENT = MC_WALK + GRADIENT;
    public static final String MC_WALK_COMFORT = MC_WALK + COMFORT;
    public static final String MC_WALK_ATTRACTIVENESS = MC_WALK + ATTRACTIVENESS;
    public static final String MC_WALK_STRESS_LINK = MC_WALK + LINK_STRESS;
    public static final String MC_WALK_STRESS_JCT = MC_WALK + JUNCTION_STRESS;


    // Parameters for bike JIBE routing
    private static final String MC_BIKE = "mc.bike.";
    public static final String MC_BIKE_TIME = MC_BIKE + TIME;
    public static final String MC_BIKE_DIST = MC_BIKE + DISTANCE;
    public static final String MC_BIKE_GRADIENT = MC_BIKE + GRADIENT;
    public static final String MC_BIKE_COMFORT = MC_BIKE + COMFORT;
    public static final String MC_BIKE_ATTRACTIVENESS = MC_BIKE + ATTRACTIVENESS;
    public static final String MC_BIKE_STRESS_LINK = MC_BIKE + LINK_STRESS;
    public static final String MC_BIKE_STRESS_JCT = MC_BIKE + JUNCTION_STRESS;


}
