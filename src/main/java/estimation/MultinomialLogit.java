package estimation;

import estimation.utilities.AbstractUtilityFunction;
import org.apache.log4j.Logger;
import smile.math.matrix.Matrix;
import smile.stat.distribution.GaussianDistribution;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MultinomialLogit {

    private final static Logger logger = Logger.getLogger(MultinomialLogit.class);

    public static void run(AbstractUtilityFunction u, int[] y, int k, double lambda, double tol, int maxIter, String resultsFileName) {

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization factor: " + lambda);
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        FlexibleMultinomialObjective objective = new FlexibleMultinomialObjective(u, y, k, lambda);

        // Run the optimisation algorithm
        double[] w = u.getStarting();
        BFGS.Results results = BFGS.minimize(objective,u.getDynamicUtilityComponent(), w, tol, maxIter);
        logger.info("finished estimation.");

        // Approximate variance-coviariance matrix (from BFGS method) – for debugging only
        Matrix approxVarCov = Matrix.of(results.hessian);

        // Hessian computed as numerical jacobian of gradient
        Matrix numericalHessian = Matrix.of(Jacobian.richardson(objective,w));
        Matrix.EVD eigenvalues = numericalHessian.eigen().sort();
        logger.info("Eigenvalues: " + Arrays.stream(eigenvalues.wr).mapToObj(d -> String.format("%.5f",d)).collect(Collectors.joining(" , ")));

        // Variance-covariance matrix computed as inverse of hessian
        Matrix varCov = numericalHessian.inverse();
        varCov.mul(-1);

        // Standard errors
        double[] se = Arrays.stream(varCov.diag()).map(Math::sqrt).toArray();
        double[] t = tTest(w,se);
        double[] pVal = pVal(t);
        String[] sig = sig(t);

        // Print results (to complete later...)
        CoefficientsWriter.write(u,results,se,t,pVal,sig,resultsFileName);
    }


    private static double[] tTest(double[] w, double[] se) {
        int p = w.length;
        assert p == se.length;
        double[] t = new double[p];
        for(int i = 0 ; i < p ; i++) {
            t[i] = w[i] / se[i];
        }
        return t;
    }

    private static double[] pVal(double[] t) {
        GaussianDistribution dist = new GaussianDistribution(0,1);
        double[] p = new double[t.length];
        for(int i = 0 ; i < t.length ; i++) {
            p[i] = 2 * (1 - dist.cdf(Math.abs(t[i])));
        }
        return p;
    }

    private static String[] sig(double[] t) {
        double[] p = pVal(t);
        String[] result = new String[t.length];
        for(int i = 0 ; i < t.length ; i++) {
            String sig;
            if(p[i] <= 0.001 ) {
                sig = "***";
            } else if(p[i] <= 0.01) {
                sig = "**";
            } else if(p[i] <= 0.05) {
                sig = "*";
            } else if(p[i] <= 0.1) {
                sig = ".";
            } else {
                sig = "";
            }
            result[i] = String.format("%.4f",p[i]) + sig;
        }
        return result;
    }

}
