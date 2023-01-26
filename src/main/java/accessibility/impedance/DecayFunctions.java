package accessibility.impedance;

public class DecayFunctions {

    public final static DecayFunction WALK_DIST = new Hansen(0.001203989);
    public final static DecayFunction WALK_DIST2 =  new Hansen(0.001086178);

    public final static DecayFunction WALK_JIBE = new Hansen(0.1147573);
    public final static DecayFunction WALK_JIBE2 =new Hansen(0.0974147);

    public final static DecayFunction BIKE_JIBE = new Hansen(0.04950999);
    public final static DecayFunction BIKE_JIBE2 = new Hansen(0.0800476);

    public final static DecayFunction BIKE_DIST = new Hansen(0.0003104329);

    public final static DecayFunction BIKE_DIST_FOOD = new Hansen(0.0005630586);
    public final static DecayFunction WALK_DIST_FOOD = new Hansen(0.001203989);

}
