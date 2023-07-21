package accessibility.decay;

// Implements a cumulative-gaussian decay function as proposed in Vale and Pereira (2017)
// https://doi.org/10.1177%2F0265813516641685

public class Gaussian extends DecayFunction {

    final private double v;

    public Gaussian(double v, Double cutoffDist, Double cutoffTime) {
        super(cutoffTime, cutoffDist);
        this.v = v;
    }

    public Gaussian(double v) {
        super(Double.NaN, Double.NaN);
        this.v = v;
    }

    public double getDecay(double cost) {
        return Math.exp(-1 * cost * cost / v);
    }

}
