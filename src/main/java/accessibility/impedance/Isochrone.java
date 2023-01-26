package accessibility.impedance;

public class Isochrone {

    double cutoff;

    public Isochrone(double cutoff) {
        this.cutoff = cutoff;
    }

    public double getDecay(double cost) {
        return cost <= cutoff ? 1. : 0.;
    }

}
