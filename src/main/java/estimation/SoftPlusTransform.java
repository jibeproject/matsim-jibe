package estimation;

// Example code for implementing softplus transforms to the SLE coefficients
// Currently disabled

public final class SoftPlusTransform {

    private SoftPlusTransform() {
    }

    public static double transform(double x) {
        return x;
//        return softplus(x);
//        return softplus(x,4);
    }

    public static double transformDerivative(double x) {
        return 1.;
//        return softplusDerivative(x);
//        return softplusDerivative(x,4);
    }

    private static double softplus(double x) {
        if (x > 0.0) {
            return x + Math.log1p(Math.exp(-x));
        } else {
            return Math.log1p(Math.exp(x));
        }
    }

    private static double softplusDerivative(double x) {
        if (x >= 0.0) {
            double z = Math.exp(-x);
            return 1.0 / (1.0 + z);
        } else {
            double z = Math.exp(x);
            return z / (1.0 + z);
        }
    }

    private static double softplus(double x, double beta) {
        if (beta <= 0.0) {
            throw new IllegalArgumentException("beta must be positive");
        }

        double bx = beta * x;

        if (bx < -20.) {
            return 0.;
        } else if (bx > 0.0) {
            return (bx + Math.log1p(Math.exp(-bx))) / beta;
        } else {
            return Math.log1p(Math.exp(bx)) / beta;
        }
    }

    private static double softplusDerivative(double x, double beta) {
        if (beta <= 0.0) {
            throw new IllegalArgumentException("beta must be positive");
        }

        double bx = beta * x;

        if (bx < -20.) {
            return 0.;
        } else if (bx >= 0.0) {
            double z = Math.exp(-bx);
            return 1.0 / (1.0 + z);
        } else {
            double z = Math.exp(bx);
            return z / (1.0 + z);
        }
    }
}
