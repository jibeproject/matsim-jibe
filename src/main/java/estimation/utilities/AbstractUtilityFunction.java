package estimation.utilities;
import estimation.dynamic.DynamicUtilityComponent;
import estimation.LogitData;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import smile.util.IntSet;

import java.util.*;
import java.util.function.IntToDoubleFunction;

public abstract class AbstractUtilityFunction {

    Logger logger = Logger.getLogger(AbstractUtilityFunction.class);
    private final DynamicUtilityComponent dynamicUtilityComponent;
    private final LogitData db;
    private final int allCoeffs;
    private final int varCoeffs;
    private final int choiceCount;
    private final IntSet choiceSet;
    private final IntSet coeffSet;
    private final Map<String,Integer> coefficients;
    private final double[] starting;
    private final boolean[] fixed;
    private final boolean[][] availability;
    private final IntToDoubleFunction[][] derivativeMatrix;

    public AbstractUtilityFunction(LogitData db, int... choiceValues) {
        this.db = db;
        this.choiceSet = new IntSet(choiceValues);
        coefficients = new HashMap<>();

        // INITIALISE COEFFICIENTS
        LinkedHashMap<String,Double> coeffs = coefficients();
        logger.info("Initialised " + coeffs.size() + " coefficients.");

        // SET FIXED COEFFICIENTS
        List<String> fixedCoeffs = fixed();
        for(String fixedCoeff : fixedCoeffs) {
            if(!coefficients().containsKey(fixedCoeff)) {
                throw new RuntimeException("Fixed coefficient " + fixedCoeff + " not in coefficients map!");
            }
        }

        // SIZE VARIABLES
        allCoeffs = coeffs.size();
        varCoeffs = allCoeffs - fixedCoeffs.size();
        choiceCount = choiceSet.size();

        // FIXED, STARTING, AND INDEX ARRAYS
        fixed = new boolean[allCoeffs];
        starting = new double[allCoeffs];
        int[] values = new int[varCoeffs];
        int i = 0;
        int j = 0;
        for(Map.Entry<String,Double> e : coeffs.entrySet()) {
            fixed[i] = fixedCoeffs.contains(e.getKey());
            starting[i] = e.getValue();
            coefficients.put(e.getKey(),i);
            if(!fixed[i]) {
                values[j] = i;
                j++;
            }
            i++;
        }
        assert(values[varCoeffs - 1] != 0);
        coeffSet = new IntSet(values);

        // COMPUTE AVAILABILITY AS MATRIX
        this.availability = computeAvailability();

        // SET DERIVATIVES
        derivativeMatrix = createDerivativeMatrix();
        logger.info("Initialised utility function with " + allCoeffs + " coefficients " +
                "(" + fixedCoeffs.size() + " fixed).");

        // SET DYNAMIC UTILITY COMPONENT (IF APPLICABLE)
        this.dynamicUtilityComponent = dynamic();
    }

    abstract LinkedHashMap<String,Double> coefficients();

    abstract List<String> fixed();

    abstract double utility(int i, int choice, double[] coefficients);

    abstract Map<String,IntToDoubleFunction[]> derivatives();

    DynamicUtilityComponent dynamic() {
        return null;
    }

    List<String> availability() {
        return new ArrayList<>();
    }

    private IntToDoubleFunction[][] createDerivativeMatrix() {
        Map<String,IntToDoubleFunction[]> derivativesMap = derivatives();
        IntToDoubleFunction[][] result = new IntToDoubleFunction[choiceCount][allCoeffs];
        for(Map.Entry<String,IntToDoubleFunction[]> e : derivativesMap.entrySet()) {
            int pos = coefficients.get(e.getKey());
            if(e.getValue().length != choiceCount) {
                throw new RuntimeException("Incorrect number of derivatives given for coefficient: " + e.getKey());
            }
            for(int k = 0 ; k < choiceCount ; k++) {
                result[k][pos] = e.getValue()[k];
            }
        }
        return result;
    }

    public double getUtility(int i, int choiceIdx, double[] c) {
        if(availability[choiceIdx][i]) {
            return utility(i, choiceSet.valueOf(choiceIdx),c);
        } else {
            return Double.NEGATIVE_INFINITY;
        }
    }

    public DynamicUtilityComponent getDynamicUtilityComponent() {
        return this.dynamicUtilityComponent;
    }

    double beta(double[] c, String name) {
        return c[coefficients.get(name)];
    }

    public double value(int i, String col) {
        return db.getValue(i,col);
    }

    public double getDerivative(int i, int choiceIdx, int coeffIdx) {
        if(availability[choiceIdx][i]) {
            return derivativeMatrix[choiceIdx][coeffSet.valueOf(coeffIdx)].applyAsDouble(i);
        } else {
            return 0;
        }
    }

    public int variableCoefficientCount() {
        return varCoeffs;
    }

    public double[] getStarting() {
        double[] values = new double[varCoeffs];
        for(int j = 0 ; j < allCoeffs ; j++) {
            if(!fixed[j]) {
                values[coeffSet.indexOf(j)] = starting[j];
            }
        }
        return values;
    }

    // Pre-computes availability for all records
    boolean[][] computeAvailability() {
        int records = db.getChoices().length;
        List<String> availablityAttributes = availability();
        boolean[][] availability = new boolean[choiceCount][records];
        for (int i = 0; i < choiceCount; i++) {
            String attr = availablityAttributes.get(i);
            if (attr == null) {
                Arrays.fill(availability[i], true);
            } else {
                for (int j = 0; j < records; j++) {
                    double avail = value(j, attr);
                    if (avail == 1) {
                        availability[i][j] = true;
                    } else if (avail == 0) {
                        availability[i][j] = false;
                    } else {
                        throw new RuntimeException("Unknown availability value " + avail + " (" + attr + " record " + j + ").");
                    }
                }
            }
        }
        return availability;
    }

    public double[] expandCoeffs(double[] w) {
        double[] wFull = new double[allCoeffs];
        for(int i = 0 ; i < allCoeffs ; i++) {
            if(fixed[i]) {
                wFull[i] = starting[i];
            } else {
                wFull[i] = w[coeffSet.indexOf(i)];
            }
        }
        return wFull;
    }

    public String[] expand(double[] z) {
        String[] result = new String[allCoeffs];
        for(int i = 0 ; i < allCoeffs ; i++) {
            if(fixed[i]) {
                result[i] = "";
            } else {
                result[i] = String.valueOf(z[coeffSet.indexOf(i)]);
            }
        }
        return result;
    }


    public String[] expand(Object[] z) {
        String[] result = new String[allCoeffs];
        for(int i = 0 ; i < allCoeffs ; i++) {
            if(fixed[i]) {
                result[i] = "";
            } else {
                result[i] = String.valueOf(z[coeffSet.indexOf(i)]);
            }
        }
        return result;
    }

    protected class DerivativesBuilder {

        Map<String,IntToDoubleFunction[]> derivativesMap;

        DerivativesBuilder() {
            derivativesMap = new HashMap<>();
        }

        void put(String name, IntToDoubleFunction... derivatives) {
            checkName(name);
            if(derivatives.length != choiceCount) {
                throw new RuntimeException("Incorrect number of derivatives entered for coefficient: " + name);
            }
            derivativesMap.put(name,derivatives);
        }

        void putAt(String name, IntToDoubleFunction derivative, int... choices) {
            checkName(name);
            IntToDoubleFunction[] derivatives = new IntToDoubleFunction[choiceCount];
            for(int i = 0 ; i < choiceCount ; i++) {
                if(ArrayUtils.contains(choices,choiceSet.valueOf(i))) {
                    derivatives[i] = derivative;
                } else {
                    derivatives[i] = j -> 0;
                }
            }
            derivativesMap.put(name,derivatives);
        }

        private void checkName(String name) {
            if(!coefficients.containsKey(name)) {
                throw new RuntimeException("Coefficient " + name + " doesn't exist in coefficients list. Cannot add derivative!");
            }
            if(derivativesMap.containsKey(name)) {
                throw new RuntimeException("Derivative already specified for coefficient: " + name);
            }
        }

        Map<String, IntToDoubleFunction[]> build() {
            if(!coefficients.keySet().containsAll(derivativesMap.keySet())) {
                Set<String> coeffs = new HashSet<>(coefficients.keySet());
                coeffs.removeAll(derivativesMap.keySet());
                throw new RuntimeException("Missing derivatives for the following coefficients: " + Arrays.toString(coeffs.toArray()));
            }
            return derivativesMap;
        }
    }

    public Set<String> getCoeffNames() {
        return coefficients().keySet();
    }

    public int getCoeffIdx(String coeffName) {
        return this.coeffSet.indexOf(coefficients.get(coeffName));
    }
}
