package accessibility.impedance;

public class HansenWithCostCutoff {

    final private double beta;
    final private double cutoff;

    public HansenWithCostCutoff(double beta, double cutoff) {
        this.beta = beta;
        this.cutoff = cutoff;
    }

    public double getDecay(double cost) {
        if(cost < cutoff) {
            return Math.exp(-1 * beta * cost);
        } else return 0;
    }


}
