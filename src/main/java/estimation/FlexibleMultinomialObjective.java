package estimation;

import estimation.utilities.AbstractUtilityFunction;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.MathEx;

import java.util.Arrays;
import java.util.stream.IntStream;

public class FlexibleMultinomialObjective implements DifferentiableMultivariateFunction {

    final AbstractUtilityFunction u;
    final int[] y;
    final int k;
    final int p;
    final double lambda;
    final int partitionSize;
    final int partitions;
    final double[][] gradients;
    final double[][] posterioris;


    FlexibleMultinomialObjective(AbstractUtilityFunction u, int[] y, int k, double lambda) {

        this.u = u;
        this.y = y;
        this.k = k;
        this.lambda = lambda;
        this.p = u.variableCoefficientCount();

        partitionSize = Integer.parseInt(System.getProperty("smile.data.partition.size", "1000"));
        partitions = y.length / partitionSize + (y.length % partitionSize == 0 ? 0 : 1);
        gradients = new double[partitions][p+1]; // todo: why do we need the +1?
        posterioris = new double[partitions][k];
    }

    @Override
    public double f(double[] w) {
        double[] wAll = u.expandCoeffs(w);
        double f = IntStream.range(0, partitions).parallel().mapToDouble(r -> {
            double[] posteriori = posterioris[r];

            int begin = r * partitionSize;
            int end = (r+1) * partitionSize;
            if (end > y.length) end = y.length;

            return IntStream.range(begin, end).sequential().mapToDouble(i -> {
                for (int j = 0; j < k; j++) {
                    posteriori[j] = u.getUtility(i,j,wAll);
                }

                MathEx.softmax(posteriori);

                return -MathEx.log(posteriori[y[i]]);
            }).sum();
        }).sum();

//        if (lambda > 0.0) { // todo: doesn't work at the moment so just keep lambda = 0
//            double wnorm = 0.0;
//            for (int i = 0; i < k-1; i++) {
//                for (int j = 0, pos = i * (p+1); j < p; j++) {
//                    double wi = w[pos + j];
//                    wnorm += wi * wi;
//                }
//            }
//            f += 0.5 * lambda * wnorm;
//        }

        return f;
    }

    @Override
    public double g(double[] w, double[] g) {
        double[] wAll = u.expandCoeffs(w);
        double f = IntStream.range(0, partitions).parallel().mapToDouble(r -> {
            double[] posteriori = posterioris[r];
            double[] gradient = gradients[r];
            Arrays.fill(gradient, 0.0);

            int begin = r * partitionSize;
            int end = (r+1) * partitionSize;
            if (end > y.length) end = y.length;

            return IntStream.range(begin, end).sequential().mapToDouble(i -> {
                for (int j = 0; j < k; j++) {
                    posteriori[j] = u.getUtility(i,j,wAll);
                }

                MathEx.softmax(posteriori);

                for (int j = 0; j < k; j++) {
                    double err = (y[i] == j ? 1.0 : 0.0) - posteriori[j];
                    for (int l = 0; l < p; l++) {
                        gradient[l] -= err * u.getDerivative(i,j,l);
                    }
                }

                return -MathEx.log(posteriori[y[i]]);
            }).sum();
        }).sum();

        Arrays.fill(g, 0.0);
        for (double[] gradient : gradients) {
            for (int i = 0; i < g.length; i++) {
                g[i] += gradient[i];
            }
        }

//        if (lambda > 0.0) { // todo: fix (same as above)
//            double wnorm = 0.0;
//            for (int i = 0; i < k-1; i++) {
//                for (int j = 0, pos = i * (p+1); j < p; j++) {
//                    double wi = w[pos + j];
//                    wnorm += wi * wi;
//                    g[pos + j] += lambda * wi;
//                }
//            }
//            f += 0.5 * lambda * wnorm;
//        }

        return f;
    }
}