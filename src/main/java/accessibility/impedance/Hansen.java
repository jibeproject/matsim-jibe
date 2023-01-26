package accessibility.impedance;

public class Hansen implements DecayFunction {
    final private double beta;

    public Hansen(double beta) {
        this.beta = beta;
    }

    public double getDecay(double cost) {
        return Math.exp(-1 * beta * cost);
    }
}
