package accessibility.impedance;

public class Isochrone extends DecayFunction {

    public Isochrone(double time, double distance) {
        super(time,distance);
        if(Double.isNaN(time) && Double.isNaN(distance)) {
            throw new RuntimeException("Specified isochrone decay function but no cutoff values given!");
        }
    }

    public double getDecay(double cost) {
        return 1.;
    }
}
