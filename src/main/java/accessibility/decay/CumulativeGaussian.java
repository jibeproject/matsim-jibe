package accessibility.decay;

// Implements a cumulative-gaussian decay function as proposed in Vale and Pereira (2017)

public class CumulativeGaussian extends DecayFunction {

    final private double a;
    final private double v;

    public CumulativeGaussian(double a, double v, Double cutoffDist, Double cutoffTime) {
        super(cutoffTime, cutoffDist);
        this.a = a;
        this.v = v;
    }

    public CumulativeGaussian(double a, double v) {
        super(Double.NaN, Double.NaN);
        this.a = a;
        this.v = v;
    }

    public double getDecay(double cost) {
        return cost <= a ? 1. : Math.exp(-1 * (cost - a) * (cost - a) / v);
    }

}
