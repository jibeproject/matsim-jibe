package accessibility.impedance;

public class HansenWithDistanceCutoff implements DecayFunction {
    final private double beta;
    final private double cutoff;

    public HansenWithDistanceCutoff(double alpha, double beta, double distanceCutoff) {
        this.beta = beta;
        this.cutoff = distanceCutoff;
    }

    public double getDecay(double cost) {
        throw new RuntimeException("Improper use of decay function with distance cutoff");
    }

    public double getDecay(double cost, double distance) {
        if(withinCutoff(distance)) {
            return Math.exp(-1 * beta * cost);
        } else return 0;
    }

    public boolean withinCutoff(double distance) {
        return distance <= cutoff;
    }
}
