package estimation;

import java.util.Arrays;

// Useful guidance on Richardson method here: https://services.math.duke.edu/~jtwong/math563-2020/lectures/Lec2-derivatives.pdf
// See numDeriv version used in apollo here: https://github.com/cran/numDeriv/blob/master/R/numDeriv.R

public class Jacobian {

    private final static double EPS = 1e-4;
    private final static double D = 0.0001;
    private final static int R = 4;

    public static void main(String[] args) {
        double[] a = new double[] {
                (exp2x(0.1) - exp2x(-0.1))/0.2,
                (exp2x(0.05) - exp2x(-0.05))/0.1,
                (exp2x(0.025) - exp2x(-0.025))/0.05,
                (exp2x(0.0125) - exp2x(-0.0125))/0.025};
        double test = extrapolate(a);
        System.out.println("test = " + test);
    }

    public static double exp2x(double x) {
        return Math.exp(2 * x);
    }

    public static double[][] simple(MultinomialLogitObjective objective, double[] x) {

        int n = x.length;

        double[] f = evaluate(objective,x);
        double[][] df = new double[n][n];

        for(int i = 0 ; i < n ; i++) {
            double[] dx = Arrays.copyOf(x,n);
            dx[i] = dx[i] + EPS;
            double[] f_dx = evaluate(objective,dx);
            for(int j = 0 ; j < n ; j++) {
                df[j][i] = (f[j] - f_dx[j]) / EPS; // negative, for some reason
            }
        }

        return df;
    }

    public static double[][] richardson(MultinomialLogitObjective objective, double[] x) {

        int n = x.length;

        double[][][] a = new double[n][n][R];

        double[] h = new double[n];
        for (int i = 0; i < n; i++) {
            h[i] = Math.abs(D * x[i]);
        }

        // A_k
        for (int k = 0; k < R; k++) {
            for (int i = 0; i < n; i++) {
                double[] x_left = Arrays.copyOf(x, n);
                double[] x_right = Arrays.copyOf(x, n);
                x_left[i] = x_left[i] - h[i];
                x_right[i] = x_right[i] + h[i];
                double[] f_left = evaluate(objective, x_left);
                double[] f_right = evaluate(objective, x_right);
                for (int j = 0; j < n; j++) {
                    a[j][i][k] = (f_left[j] - f_right[j]) / (2 * h[i]);
                }
            }
            for (int j = 0; j < n; j++) {
                h[j] = h[j] / 2;
            }
        }

        // Richardson extrapolation
        double[][] df = new double[n][n];
        for(int i = 0 ; i < n ; i++) {
            for(int j = 0 ; j < n ; j++) {
                df[i][j] = extrapolate(a[i][j]);
            }
        }
        return df;
    }

    public static double extrapolate(double[] a) {
        return extrapolate(a,1);
    }

    public static double extrapolate(double[] a,int m) {
        if(a.length == 1) {
            return a[0];
        }
        double[] a_k = new double[a.length - 1];
        for(int j = 0 ; j < a.length - 1 ; j++) {
            a_k[j] = (Math.pow(4,m)*a[j+1] - a[j]) / (Math.pow(4,m) - 1);
        }
        return extrapolate(a_k,m+1);
    }

    public static double[] evaluate(MultinomialLogitObjective objective, double[] x) {
        double[] result = new double[x.length];
        objective.g(x,result);
        return result;
    }

}
