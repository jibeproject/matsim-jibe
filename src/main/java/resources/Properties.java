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
    public static final String MATSIM_CAR_NETWORK = "matsim.car.network";
    public static final String MATSIM_TRANSIT_NETWORK = "matsim.transit.network";
    public static final String MATSIM_TRANSIT_SCHEDULE = "matsim.transit.schedule";

    public static final String MATSIM_DEMAND_CONFIG = "matsim.demand.config";
    public static final String MATSIM_DEMAND_SCALE_FACTOR = "matsim.demand.scale.factor";
    public static final String MATSIM_DEMAND_VEHICLES = "matsim.demand.vehicles";
    public static final String MATSIM_DEMAND_PLANS = "matsim.demand.plans";
    public static final String MATSIM_DEMAND_OUTPUT_EVENTS = "matsim.demand.output.events";
//    public static final String MATSIM_DEMAND_OUTPUT_NETWORK = "matsim.demand.output.network";
    public static final String MATSIM_DEMAND_OUTPUT_VEHICLES = "matsim.demand.output.vehicles";
    public static final String MATSIM_DEMAND_OUTPUT_SCALE_FACTOR = "matsim.demand.output.scale.factor";

    // Other properties
    public static final String NUMBER_OF_THREADS = "number.of.threads";
    public static final String MAX_BIKE_SPEED = "max.bike.speed";
    public static final String DECAY_PERCENTILE = "decay.percentile";
    
    // Survey data attribute names
    public static final String DIARY_FILE = "diary.file";
    public static final String DIARY_DELIMITER = "diary.delimiter";
    public static final String HOUSEHOLD_ID = "diary.household.id";
    public static final String PERSON_ID = "diary.person.id";
    public static final String TRIP_ID = "diary.trip.id";
    public static final String START_TIME = "diary.start.time";
    public static final String MAIN_MODE = "diary.main.mode";
    public static final String ORIGIN_PURPOSE = "diary.origin.purpose";
    public static final String DESTINATION_PURPOSE = "diary.destination.purpose";
    public static final String HOME_ZONE = "diary.home.zone";
    public static final String MAIN_ZONE = "diary.main.zone";
    public static final String ORIGIN_ZONE = "diary.origin.zone";
    public static final String DESTINATION_ZONE = "diary.destination.zone";
    public static final String HOME_X = "diary.home.x";
    public static final String HOME_Y = "diary.home.y";
    public static final String MAIN_X = "diary.main.x";
    public static final String MAIN_Y = "diary.main.y";
    public static final String ORIGIN_X = "diary.origin.x";
    public static final String ORIGIN_Y = "diary.origin.y";
    public static final String DESTINATION_X = "diary.destination.x";
    public static final String DESTINATION_Y = "diary.destination.y";

    // Parameters for JIBE routing (generic)
    public static final String TIME = "time";
    public static final String DISTANCE = "dist";
    public static final String GRADIENT = "gradient";
    public static final String COMFORT = "comfort";
    public static final String AMBIENCE = "ambience";
    public static final String STRESS = "stress";
    
    // Parameters for walk JIBE routing
    private static final String MC_WALK = "mc.walk.";
    public static final String MC_WALK_TIME = MC_WALK + TIME;
    public static final String MC_WALK_DIST = MC_WALK + DISTANCE;
    public static final String MC_WALK_GRADIENT = MC_WALK + GRADIENT;
    public static final String MC_WALK_COMFORT = MC_WALK + COMFORT;
    public static final String MC_WALK_AMBIENCE = MC_WALK + AMBIENCE;
    public static final String MC_WALK_STRESS = MC_WALK + STRESS;


    // Parameters for bike JIBE routing
    private static final String MC_BIKE = "mc.bike.";
    public static final String MC_BIKE_TIME = MC_BIKE + TIME;
    public static final String MC_BIKE_DIST = MC_BIKE + DISTANCE;
    public static final String MC_BIKE_GRADIENT = MC_BIKE + GRADIENT;
    public static final String MC_BIKE_COMFORT = MC_BIKE + COMFORT;
    public static final String MC_BIKE_AMBIENCE = MC_BIKE + AMBIENCE;
    public static final String MC_BIKE_STRESS = MC_BIKE + STRESS;


}
