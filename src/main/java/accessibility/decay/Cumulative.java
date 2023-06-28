package accessibility.decay;

public class Cumulative extends DecayFunction {

    public Cumulative(double time, double distance) {
        super(time,distance);
        if(Double.isNaN(time) && Double.isNaN(distance)) {
            throw new RuntimeException("Specified cumulative decay function but no cutoff values given!");
        }
    }

    public double getDecay(double cost) {
        return 1.;
    }
}
