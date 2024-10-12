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

import estimation.dynamic.DynamicComponent;
import org.apache.log4j.Logger;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.MultivariateFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
public class BFGS {
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
    public static Results minimize(DifferentiableMultivariateFunction func, DynamicComponent dynamicComponent, double[] x, double gtol, int maxIter) {

        List<double[]> xAtEachIteration = new ArrayList<>();
        List<Double> fAtEachIteration = new ArrayList<>();

        if (gtol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + gtol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        double den, fac, fad, fae, sumdg, sumxi, temp, test;

        int n = x.length;
        double[] dg = new double[n];
        double[] g = new double[n];
        double[] hdg = new double[n];
        double[] xnew = new double[n];
        double[] xi = new double[n];
        double[][] hessin = new double[n][n];

        // Calculate starting function value and gradient and initialize the
        // inverse Hessian to the unit matrix.
        double f = func.g(x, g);
        double llStart = f;
        logger.info(String.format("BFGS: initial function value: %.5f", f));

        for (int i = 0; i < n; i++) {
            hessin[i][i] = 1.0;
            // Initialize line direction.
            xi[i] = -g[i];
        }

        double stpmax = STPMX * max(norm(x), n);

        xAtEachIteration.add(Arrays.copyOf(x,x.length));
        fAtEachIteration.add(f);

        for (int iter = 1; iter <= maxIter; iter++) {

            // The new function evaluation occurs in line search.
            f = linesearch(func, x, f, g, xi, xnew, stpmax, dynamicComponent);

            logger.info(String.format("BFGS: the function value after %3d iterations: %.5f", iter, f));

            // update the line direction and current point.
            for (int i = 0; i < n; i++) {
                xi[i] = xnew[i] - x[i];
                x[i] = xnew[i];
            }

            // Store for debugging
            xAtEachIteration.add(Arrays.copyOf(x,x.length));
            fAtEachIteration.add(f);

            // Test for convergence on x.
            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = abs(xi[i]) / max(abs(x[i]), 1.0);
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < TOLX) {
                logger.info(String.format("BFGS converges on x after %d iterations: %.5f", iter, f));
                return new Results(llStart,f,hessin,iter,xAtEachIteration,fAtEachIteration);
            }

            System.arraycopy(g, 0, dg, 0, n);

            func.g(x, g);

            // Test for convergence on zero gradient.
            den = max(f, 1.0);
            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = abs(g[i]) * max(abs(x[i]), 1.0) / den;
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < gtol) {
                logger.info(String.format("BFGS converges on gradient after %d iterations: %.5f", iter, f));
                return new Results(llStart,f,hessin,iter,xAtEachIteration,fAtEachIteration);
            }

            for (int i = 0; i < n; i++) {
                dg[i] = g[i] - dg[i];
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

            // Skip update if fac is not sufficiently positive.
            if (fac > sqrt(EPSILON * sumdg * sumxi)) {
                fac = 1.0 / fac;
                fad = 1.0 / fae;

                // The vector that makes BFGS different from DFP.
                for (int i = 0; i < n; i++) {
                    dg[i] = fac * xi[i] - fad * hdg[i];
                }

                // BFGS updating formula.
                for (int i = 0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        hessin[i][j] += fac * xi[i] * xi[j] - fad * hdg[i] * hdg[j] + fae * dg[i] * dg[j];
                        hessin[j][i] = hessin[i][j];
                    }
                }
            }

            // Calculate the next direction to go.
            Arrays.fill(xi, 0.0);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    xi[i] -= hessin[i][j] * g[j];
                }
            }
        }

        logger.warn(String.format("BFGS reaches maximum %d iterations: %.5f", maxIter, f));
        return new Results(llStart,f,hessin,maxIter,xAtEachIteration,fAtEachIteration);

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
                                     DynamicComponent dynamicComponent) {
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

            // Update dynamic component
            if(dynamicComponent != null) {
                dynamicComponent.update(x);
            }

            double f = func.apply(x);

            // Convergence on &Delta; x.
            if (alam < alammin) {
                System.arraycopy(xold, 0, x, 0, n);
                logger.info("Linesearch ran " + runCount + " times, no update.");
                // Go back to old dynamic component (unlikely)
                if(dynamicComponent != null) {
                    dynamicComponent.update(x);
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
}
