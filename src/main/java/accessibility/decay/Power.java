package accessibility.decay;

public class Power extends DecayFunction {
    final private double a;

    public Power(double a, Double cutoffDist, Double cutoffTime) {
        super(cutoffTime, cutoffDist);
        this.a = a;
    }

    public Power(double a) {
        super(Double.NaN, Double.NaN);
        this.a = a;
    }

    public double getDecay(double cost) {
        return Math.pow(cost,-1 * a);
    }

}
