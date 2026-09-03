/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package estimation;

import estimation.latent.LatentUtilityComponent;
import org.apache.log4j.Logger;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.MultivariateFunction;
import smile.math.matrix.Matrix;
import smile.sort.QuickSort;

import java.util.*;

import static java.lang.Math.*;
import static smile.math.MathEx.*;

/**
 * The Broyden–Fletcher–Goldfarb–Shanno (BFGS) algorithm is an iterative
 * method for solving unconstrained nonlinear optimization problems.
 * <p>
 * The BFGS method belongs to quasi-Newton methods, a class of hill-climbing
 * optimization techniques that seek a stationary point of a (preferably
 * twice continuously differentiable) function. For such problems,
 * a necessary condition for optimality is that the gradient be zero.
 * Newton's method and the BFGS methods are not guaranteed to converge
 * unless the function has a quadratic Taylor expansion near an optimum.
 * However, BFGS has proven to have good performance even for non-smooth
 * optimizations.
 * <p>
 * In quasi-Newton methods, the Hessian matrix of second derivatives is
 * not computed. Instead, the Hessian matrix is approximated using
 * updates specified by gradient evaluations (or approximate gradient
 * evaluations). Quasi-Newton methods are generalizations of the secant
 * method to find the root of the first derivative for multidimensional
 * problems. In multi-dimensional problems, the secant equation does not
 * specify a unique solution, and quasi-Newton methods differ in how they
 * constrain the solution. The BFGS method is one of the most popular
 * members of this class.
 * <p>
 * Like the original BFGS, the limited-memory BFGS (L-BFGS) uses an
 * estimation to the inverse Hessian matrix to steer its search
 * through variable space, but where BFGS stores a dense {@code n × n}
 * approximation to the inverse Hessian (<code>n</code> being the number of
 * variables in the problem), L-BFGS stores only a few vectors
 * that represent the approximation implicitly. Due to its resulting
 * linear memory requirement, the L-BFGS method is particularly well
 * suited for optimization problems with a large number of variables
 * (e.g., {@code > 1000}). Instead of the inverse Hessian <code>H_k</code>, L-BFGS
 * maintains * a history of the past <code>m</code> updates of the position
 * <code>x</code> and gradient <code>∇f(x)</code>, where generally the
 * history size <code>m</code> can be small (often {@code m < 10}).
 * These updates are used to implicitly do operations requiring the
 * <code>H_k</code>-vector product.
 *
 * <h2>References</h2>
 * <ol>
 *     <li>Roger Fletcher. Practical methods of optimization.</li>
 *     <li>D. Liu and J. Nocedal. On the limited memory BFGS method for large scale optimization. Mathematical Programming B 45 (1989) 503-528.</li>
 *     <li>Richard H. Byrd, Peihuang Lu, Jorge Nocedal and Ciyou Zhu. A limited memory algorithm for bound constrained optimization.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class BFGSB {
    private static final Logger logger = Logger.getLogger(BFGS.class);
    /** A number close to zero, between machine epsilon and its square root. */
    private static final double EPSILON = Double.parseDouble(System.getProperty("smile.bfgs.epsilon", "1E-8"));
    /** The convergence criterion on x values. */
    private static final double TOLX = 4 * EPSILON;
    /** The convergence criterion on function value. */
    private static final double TOLF = 4 * EPSILON;
    /** The scaled maximum step length allowed in line searches. */
    private static final double STPMX = 100.0;

    public static class Results {

        final double llStart;
        final double llOut;
        final double[][] hessian;
        final int iterations;
        final List<double[]> xAtEachIteration;

        final List<Double> lAtEachIteration;

        Results(double llStart, double llOut, double[][] hessian, int iterations, List<double[]> xAtEachIteration, List<Double> lAtEachIteration) {
            this.llStart = llStart;
            this.llOut = llOut;
            this.hessian = hessian;
            this.iterations = iterations;
            this.xAtEachIteration = xAtEachIteration;
            this.lAtEachIteration = lAtEachIteration;
        }
    }

    /**
     * This method solves the unconstrained minimization problem
     * <pre>
     *     min f(x),    x = (x1,x2,...,x_n),
     * </pre>
     * using the BFGS method.
     *
     * @param func the function to be minimized.
     *
     * @param x on initial entry this must be set by the user to the values
     *          of the initial estimate of the solution vector. On exit, it
     *          contains the values of the variables at the best point found
     *          (usually a solution).
     *
     * @param gtol the convergence tolerance on zeroing the gradient.
     * @param maxIter the maximum number of iterations.
     *
     * @return the minimum value of the function.
     */
    public static Results minimize(DifferentiableMultivariateFunction func, LatentUtilityComponent latentUtilityComponent,
                                   double[] x, double[] l, double[] u, double gtol, int maxIter) {

        List<double[]> xAtEachIteration = new ArrayList<>();
        List<Double> fAtEachIteration = new ArrayList<>();

        if (gtol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + gtol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        if (l.length != x.length) {
            throw new IllegalArgumentException("Invalid lower bound size: " + l.length);
        }

        if (u.length != x.length) {
            throw new IllegalArgumentException("Invalid upper bound size: " + u.length);
        }

        int n = x.length;
        for (int i = 0; i < n; i++) {
            if (l[i] > u[i]) {
                throw new IllegalArgumentException("Lower bound is greater than upper bound at index " + i);
            }
        }

        double den, fac, fad, fae, sumdg, sumxi, temp, test;

        double[] dg = new double[n];
        double[] g = new double[n];
        double[] gOld = new double[n];
        double[] hdg = new double[n];
        double[] xOld = new double[n];
        double[] xnew = new double[n];
        double[] xi = new double[n];
        double[][] hessin = new double[n][n];

        double f = func.g(x, g);
        double llStart = f;
        logger.info(String.format("BFGS-B: initial function value: %.5f", f));

        for (int i = 0; i < n; i++) {
            hessin[i][i] = 1.0;
        }

        double stpmax = STPMX * max(norm(x), n);

        xAtEachIteration.add(Arrays.copyOf(x, x.length));
        fAtEachIteration.add(f);

        for (int iter = 1; iter <= maxIter; iter++) {
            double fOld = f;
            System.arraycopy(x, 0, xOld, 0, n);
            System.arraycopy(g, 0, gOld, 0, n);

            Matrix B;
            try {
                B = Matrix.of(hessin).inverse();
            } catch (RuntimeException e) {
                logger.warn("Could not invert inverse Hessian approximation; using identity Hessian.", e);
                B = Matrix.eye(n);
            }

            double[] cauchy = cauchy(x, g, l, u, B);
            clampToBound(cauchy, l, u);

            double[] subspaceMin = subspaceMinimization(x, g, cauchy, l, u, B);
            clampToBound(subspaceMin, l, u);

            for (int i = 0; i < n; i++) {
                xi[i] = subspaceMin[i] - x[i];
            }

            if (norm(xi) == 0.0) {
                logger.info(String.format("BFGS-B converges on projected step after %d iterations: %.5f", iter, f));
                return new Results(llStart, f, hessin, iter - 1, xAtEachIteration, fAtEachIteration);
            }

            f = linesearch(func, x, f, g, xi, xnew, stpmax, latentUtilityComponent);
            clampToBound(xnew, l, u);

            if (latentUtilityComponent != null) {
                latentUtilityComponent.update(xnew);
            }

            for (double value : xnew) {
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    logger.warn("BFGS-B: bad x produced by line search, returning previous good x.");
                    System.arraycopy(xOld, 0, x, 0, n);
                    return new Results(llStart, fOld, hessin, iter - 1, xAtEachIteration, fAtEachIteration);
                }
            }

            for (int i = 0; i < n; i++) {
                xi[i] = xnew[i] - x[i];
                x[i] = xnew[i];
            }

            f = func.g(x, g);
            if (Double.isNaN(f) || Double.isInfinite(f)) {
                logger.warn("BFGS-B: bad f(x) produced by line search, returning previous good x.");
                System.arraycopy(xOld, 0, x, 0, n);
                return new Results(llStart, fOld, hessin, iter - 1, xAtEachIteration, fAtEachIteration);
            }

            logger.info(String.format("BFGS-B: the function value after %3d iterations: %.5f", iter, f));

            xAtEachIteration.add(Arrays.copyOf(x, x.length));
            fAtEachIteration.add(f);

            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = abs(xi[i]) / max(abs(x[i]), 1.0);
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < TOLX) {
                logger.info(String.format("BFGS-B converges on x after %d iterations: %.5f", iter, f));
                return new Results(llStart, f, hessin, iter, xAtEachIteration, fAtEachIteration);
            }

            if (gnorm(x, g, l, u) < gtol) {
                logger.info(String.format("BFGS-B converges on projected gradient after %d iterations: %.5f", iter, f));
                return new Results(llStart, f, hessin, iter, xAtEachIteration, fAtEachIteration);
            }

            if (abs(fOld - f) < TOLF) {
                logger.info(String.format("BFGS-B converges on f(x) after %d iterations: %.5f", iter, f));
                return new Results(llStart, f, hessin, iter, xAtEachIteration, fAtEachIteration);
            }

            for (int i = 0; i < n; i++) {
                dg[i] = g[i] - gOld[i];
            }

            for (int i = 0; i < n; i++) {
                hdg[i] = 0.0;
                for (int j = 0; j < n; j++) {
                    hdg[i] += hessin[i][j] * dg[j];
                }
            }

            fac = fae = sumdg = sumxi = 0.0;
            for (int i = 0; i < n; i++) {
                fac += dg[i] * xi[i];
                fae += dg[i] * hdg[i];
                sumdg += dg[i] * dg[i];
                sumxi += xi[i] * xi[i];
            }

            if (fac > sqrt(EPSILON * sumdg * sumxi) && abs(fae) > EPSILON) {
                fac = 1.0 / fac;
                fad = 1.0 / fae;

                for (int i = 0; i < n; i++) {
                    dg[i] = fac * xi[i] - fad * hdg[i];
                }

                for (int i = 0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        hessin[i][j] += fac * xi[i] * xi[j]
                                - fad * hdg[i] * hdg[j]
                                + fae * dg[i] * dg[j];
                        hessin[j][i] = hessin[i][j];
                    }
                }
            } else {
                logger.warn("BFGS-B: skipping inverse Hessian update because curvature condition failed.");
            }
        }

        logger.warn(String.format("BFGS-B reaches maximum %d iterations: %.5f", maxIter, f));
        return new Results(llStart, f, hessin, maxIter, xAtEachIteration, fAtEachIteration);
    }

    /**
     * Minimize a function along a search direction by find a step which satisfies
     * a sufficient decrease condition and a curvature condition.
     * <p>
     * At each stage this function updates an interval of uncertainty with
     * endpoints <code>stx</code> and <code>sty</code>. The interval of
     * uncertainty is initially chosen so that it contains a
     * minimizer of the modified function
     * <pre>{@code
     *      f(x+stp*s) - f(x) - ftol*stp*(gradf(x)'s).
     * }</pre>
     * If a step is obtained for which the modified function
     * has a nonpositive function value and non-negative derivative,
     * then the interval of uncertainty is chosen so that it
     * contains a minimizer of {@code f(x+stp*s)}.
     * <p>
     * The algorithm is designed to find a step which satisfies
     * the sufficient decrease condition
     * <pre>{@code
     *       f(x+stp*s) <= f(X) + ftol*stp*(gradf(x)'s),
     * }</pre>
     * and the curvature condition
     * <p>
     * <pre>{@code
     *       abs(gradf(x+stp*s)'s)) <= gtol*abs(gradf(x)'s).
     * }</pre>
     * If <code>ftol</code> is less than <code>gtol</code> and if, for example,
     * the function is bounded below, then there is always a step which
     * satisfies both conditions. If no step can be found which satisfies both
     * conditions, then the algorithm usually stops when rounding
     * errors prevent further progress. In this case <code>stp</code> only
     * satisfies the sufficient decrease condition.
     *
     * @param xold on input this contains the base point for the line search.
     *
     * @param fold on input this contains the value of the objective function
     *             at <code>x</code>.
     *
     * @param g on input this contains the gradient of the objective function
     *          at <code>x</code>.
     *
     * @param p the search direction.
     *
     * @param x on output, it contains <code>xold + &gamma;*p</code>, where
     *          &gamma; {@code > 0} is the step length.
     *
     * @param stpmax specify upper bound for the step in the line search so that
     *          we do not try to evaluate the function in regions where it is
     *          undefined or subject to overflow.
     *
     * @return the new function value.
     */
    private static double linesearch(MultivariateFunction func, double[] xold, double fold, double[] g, double[] p, double[] x, double stpmax,
                                     LatentUtilityComponent latentUtilityComponent) {
        if (stpmax <= 0) {
            throw new IllegalArgumentException("Invalid upper bound of linear search step: " + stpmax);
        }

        // Termination occurs when the relative width of the interval
        // of uncertainty is at most xtol.
        final double xtol = EPSILON;
        // Tolerance for the sufficient decrease condition.
        final double ftol = 1.0E-4;

        int n = xold.length;

        // Scale if attempted step is too big
        double pnorm = norm(p);
        if (pnorm > stpmax) {
            double r = stpmax / pnorm;
            for (int i = 0; i < n; i++) {
                p[i] *= r;
            }
        }

        // Check if s is a descent direction.
        double slope = 0.0;
        for (int i = 0; i < n; i++) {
            slope += g[i] * p[i];
        }

        if (slope >= 0) {
            logger.warn("Line Search: the search direction is not a descent direction, which may be caused by roundoff problem.");
        }

        // Calculate minimum step.
        double test = 0.0;
        for (int i = 0; i < n; i++) {
            double temp = abs(p[i]) / max(xold[i], 1.0);
            if (temp > test) {
                test = temp;
            }
        }

        double alammin = xtol / test;
        double alam = 1.0;

        double alam2 = 0.0, f2 = 0.0;
        double a, b, disc, rhs1, rhs2, tmpalam;
        int runCount = 0;
        while (true) {
            runCount++;
            // Evaluate the function and gradient at stp
            // and compute the directional derivative.
            for (int i = 0; i < n; i++) {
                x[i] = xold[i] + alam * p[i];
            }

            // Update latent component
            if(latentUtilityComponent != null) {
                latentUtilityComponent.update(x);
            }

            double f = func.apply(x);

            // Convergence on &Delta; x.
            if (alam < alammin) {
                System.arraycopy(xold, 0, x, 0, n);
                logger.info("Linesearch ran " + runCount + " times, no update.");
                // Go back to old latent component (unlikely)
                if(latentUtilityComponent != null) {
                    latentUtilityComponent.update(x);
                }
                return f;
            } else if (f <= fold + ftol * alam * slope) {
                // Sufficient function decrease.
                logger.info("Linesearch ran " + runCount + " times, completed with sufficient function decrease.");
                return f;
            } else {
                // Backtrack
                if (alam == 1.0) {
                    // First time
                    tmpalam = -slope / (2.0 * (f - fold - slope));
                } else {
                    // Subsequent backtracks.
                    rhs1 = f - fold - alam * slope;
                    rhs2 = f2 - fold - alam2 * slope;
                    a = (rhs1 / (alam * alam) - rhs2 / (alam2 * alam2)) / (alam - alam2);
                    b = (-alam2 * rhs1 / (alam * alam) + alam * rhs2 / (alam2 * alam2)) / (alam - alam2);
                    if (a == 0.0) {
                        tmpalam = -slope / (2.0 * b);
                    } else {
                        disc = b * b - 3.0 * a * slope;
                        if (disc < 0.0) {
                            tmpalam = 0.5 * alam;
                        } else if (b <= 0.0) {
                            tmpalam = (-b + sqrt(disc)) / (3.0 * a);
                        } else {
                            tmpalam = -slope / (b + sqrt(disc));
                        }
                    }
                    if (tmpalam > 0.5 * alam) {
                        tmpalam = 0.5 * alam;
                    }
                }
            }
            alam2 = alam;
            f2 = f;
            alam = max(tmpalam, 0.1 * alam);
        }
    }

    /**
     * Algorithm CP: Computation of the Generalized Cauchy Point. See page 8.
     * @param x  the parameter vector
     * @param g  the gradient vector
     */
    private static double[] cauchy(double[] x, double[] g, double[] l, double[] u, Matrix B) {
        int n = x.length;
        double[] t = new double[n];
        double[] d = new double[n];
        double[] z = new double[n];

        for (int i = 0; i < n; i++) {
            if (g[i] < 0.0) {
                t[i] = (x[i] - u[i]) / g[i];
                d[i] = -g[i];
            } else if (g[i] > 0.0) {
                t[i] = (x[i] - l[i]) / g[i];
                d[i] = -g[i];
            } else {
                t[i] = Double.POSITIVE_INFINITY;
                d[i] = 0.0;
            }

            if (t[i] <= 0.0) {
                d[i] = 0.0;
            }
        }

        int[] index = QuickSort.sort(t);

        double[] Bd = B.mv(d);
        double fPrime = dot(g, d);
        double fDoublePrime = dot(d, Bd);

        double tOld = 0.0;
        int i = 0;

        while (i < n) {
            int b = index[i];
            if (!Double.isFinite(t[b])) {
                break;
            }

            double dtBreak = t[b] - tOld;
            if (dtBreak < 0.0) {
                i++;
                continue;
            }

            if (fDoublePrime > EPSILON) {
                double dtMin = -fPrime / fDoublePrime;
                if (dtMin >= 0.0 && dtMin <= dtBreak) {
                    for (int j = 0; j < n; j++) {
                        z[j] += dtMin * d[j];
                    }
                    return project(x, z, l, u);
                }
            }

            for (int j = 0; j < n; j++) {
                z[j] += dtBreak * d[j];
            }

            tOld = t[b];

            while (i < n && t[index[i]] <= tOld) {
                int hit = index[i];
                d[hit] = 0.0;
                i++;
            }

            Bd = B.mv(d);
            double[] Bz = B.mv(z);
            fPrime = dot(g, d) + dot(Bz, d);
            fDoublePrime = dot(d, Bd);

            if (norm(d) == 0.0) {
                break;
            }
        }

        return project(x, z, l, u);
    }

    private static double[] project(double[] x, double[] z, double[] l, double[] u) {
        int n = x.length;
        double[] result = new double[n];

        for (int i = 0; i < n; i++) {
            result[i] = x[i] + z[i];
            if (result[i] < l[i]) {
                result[i] = l[i];
            } else if (result[i] > u[i]) {
                result[i] = u[i];
            }
        }

        return result;
    }

    /**
     * Subspace minimization using a full Hessian approximation B.
     *
     * Keeps variables that are active at the Cauchy point fixed, and minimizes
     * the quadratic model over the remaining free variables.
     *
     * @param x current parameter vector
     * @param g current gradient
     * @param c Cauchy point
     * @param l lower bounds
     * @param u upper bounds
     * @param B approximate Hessian matrix, not inverse Hessian
     */
    private static double[] subspaceMinimization(double[] x, double[] g, double[] c,
                                                 double[] l, double[] u, Matrix B) {
        int n = x.length;
        ArrayList<Integer> freeVarIdx = new ArrayList<>();

        final double boundTolerance = 1.0E-8;
        for (int i = 0; i < n; i++) {
            if (c[i] > l[i] + boundTolerance && c[i] < u[i] - boundTolerance) {
                freeVarIdx.add(i);
            }
        }

        if (freeVarIdx.isEmpty()) {
            return c.clone();
        }

        int freeVarCount = freeVarIdx.size();
        int[] freeVar = new int[freeVarCount];
        for (int i = 0; i < freeVarCount; i++) {
            freeVar[i] = freeVarIdx.get(i);
        }

        double[] z = new double[n];
        for (int i = 0; i < n; i++) {
            z[i] = c[i] - x[i];
        }

        double[] Bz = B.mv(z);
        double[] r = new double[freeVarCount];
        for (int i = 0; i < freeVarCount; i++) {
            int fi = freeVar[i];
            r[i] = g[fi] + Bz[fi];
        }

        Matrix Bff = new Matrix(freeVarCount, freeVarCount);
        for (int i = 0; i < freeVarCount; i++) {
            int row = freeVar[i];
            for (int j = 0; j < freeVarCount; j++) {
                int col = freeVar[j];
                Bff.set(i, j, B.get(row, col));
            }
        }

        double[] rhs = new double[freeVarCount];
        for (int i = 0; i < freeVarCount; i++) {
            rhs[i] = -r[i];
        }

        double[] du;
        try {
            du = Bff.lu().solve(rhs);
        } catch (RuntimeException e) {
            logger.warn("Subspace minimization failed; returning Cauchy point.", e);
            return c.clone();
        }

        double alphaStar = findAlpha(c, du, l, u, freeVar);

        double[] subspaceMin = c.clone();
        for (int i = 0; i < freeVarCount; i++) {
            subspaceMin[freeVar[i]] += alphaStar * du[i];
        }

        clampToBound(subspaceMin, l, u);
        return subspaceMin;
    }


    /**
     * Find the alpha* parameter, a positive scalar. See Equation 5.8 on page 11.
     * @param cauchy   cauchy point
     * @param du       intermediate results used to find alpha*
     @param freeVar  the indices of free variable
     */
    private static double findAlpha(double[] cauchy, double[] du, double[] l, double[] u, int[] freeVar) {
        double alphaStar = 1.0;
        int n = freeVar.length;

        for (int i = 0; i < n; i++) {
            int fi = freeVar[i];

            if (du[i] > 0.0) {
                alphaStar = min(alphaStar, (u[fi] - cauchy[fi]) / du[i]);
            } else if (du[i] < 0.0) {
                alphaStar = min(alphaStar, (l[fi] - cauchy[fi]) / du[i]);
            }
        }

        return max(0.0, min(1.0, alphaStar));
    }

    /**
     * Returns the infinity norm of  gradient.
     * @param x  the parameter vector
     * @param g  the gradient vector
     */
    private static double gnorm(double[] x, double[] g, double[] l, double[] u) {
        double norm = 0.0;
        int n = x.length;

        for (int i = 0; i < n; i++) {
            double gi = g[i];
            if (gi < 0) gi = max(x[i] - u[i], gi);
            else gi = min(x[i] - l[i], gi);
            norm = max(norm, abs(gi));
        }

        return norm;
    }

    /**
     * Clamps the values of v to stay within the pre-defined bounds.
     * @param v  the vector to be adjusted
     */
    private static void clampToBound(double[] v, double[] l, double[] u) {
        int n = v.length;

        for (int i = 0; i < n; i++) {
            if (v[i] > u[i]) v[i] = u[i];
            else if (v[i] < l[i]) v[i] = l[i];
        }
    }
}
