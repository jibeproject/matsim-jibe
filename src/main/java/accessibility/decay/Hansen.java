package accessibility.decay;

public class Hansen extends DecayFunction {
    final private double beta;

    public Hansen(double beta, Double cutoffDist, Double cutoffTime) {
        super(cutoffTime, cutoffDist);
        this.beta = beta;
    }

    public Hansen(double beta) {
        super(Double.NaN, Double.NaN);
        this.beta = beta;
    }

    public double getDecay(double cost) {
        return Math.exp(-1 * beta * cost);
    }

}
