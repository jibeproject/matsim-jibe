package accessibility.resources;

public class AccessibilityProperties {

    public static final String MODE = "mode";
    public static final String IMPEDANCE = "disutility";
    public static final String END_LOCATIONS = "end.coords";
    public static final String FORWARD = "forward"; // todo: check this works

    // Related to decay function
    public static final String DECAY_FUNCTION = "decay.function";
    public static final String CUTOFF_DISTANCE = "cutoff.distance";
    public static final String CUTOFF_TIME = "cutoff.time";
    public static final String BETA = "beta";
    public static final String V = "v";
    public static final String A = "a";

    // For accessibility analysis
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String OUTPUT_NODES = "output.nodes";

    // For Intervention
    public static final String POPULATION = "population";
    public static final String CURRENT_DESTINATIONS = "current.destinations";
    public static final String NEW_DESTINATIONS = "new.destinations";
    public static final String NEW_DESTINATION_WEIGHT = "new.destination.weight";
    public static final String NEW_DESTINATION_COUNT = "new.destination.count";

    public static final String NEW_ACCESSIBILITIES = "new.accessibilities";

    public static final String DEVELOPMENT_AREAS = "development.areas";

    // OPTIONAL: If using TRADS dataset
    public static final String PURPOSE_PAIR = "purpose.pair";
    public static final String TRADS_OUTPUT_CSV = "trads.cost.output";
}
