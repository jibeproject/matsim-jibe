package accessibility.decay;

public class Exponential extends DecayFunction {
    final private double beta;

    public Exponential(double beta, Double cutoffDist, Double cutoffTime) {
        super(cutoffTime, cutoffDist);
        this.beta = beta;
    }

    public Exponential(double beta) {
        super(Double.NaN, Double.NaN);
        this.beta = beta;
    }

    public double getDecay(double cost) {
        return Math.exp(-1 * beta * cost);
    }

}
