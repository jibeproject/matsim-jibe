package estimation.specifications;
import estimation.UtilityFunction;
import estimation.dynamic.DynamicComponent;
import estimation.LogitData;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import smile.util.IntSet;

import java.util.*;

public abstract class AbstractModelSpecification {

    static Logger logger = Logger.getLogger(AbstractModelSpecification.class);
    private final LogitData db;
    private int allCoeffs;
    private int varCoeffs;
    private int choiceCount;
    private final String[] choiceNames;
    private final IntSet choiceSet;
    private IntSet coeffSet;
    private final Map<String,Integer> coefficients;
    private double[] starting;
    private boolean[] fixed;
    private boolean[][] availability;
    private UtilityFunction[] utilityVector;
    private UtilityFunction[][] derivativeMatrix;
    private DynamicComponent dynamicComponent;

    public AbstractModelSpecification(LogitData db, boolean initialise, String[] choiceNames, int[] choiceValues) {
        this.db = db;
        this.choiceNames = choiceNames;
        this.choiceSet = new IntSet(choiceValues);
        coefficients = new HashMap<>();
        if(initialise) {
            initialiseCoeffAvail();
            initialiseDynamicUtilDeriv();
        }
    }

    protected void initialiseCoeffAvail() {
        LinkedHashMap<String,Double> coeffs = coefficients();
        logger.info("Initialised " + coeffs.size() + " coefficients.");

        // SET FIXED COEFFICIENTS
        List<String> fixedCoeffs = fixed();
        for(String fixedCoeff : fixedCoeffs) {
            long appearances = fixedCoeffs.stream().filter(f -> f.equals(fixedCoeff)).count();
            if(appearances > 1) {
                throw new RuntimeException("Fixed coefficient \"" + fixedCoeff + "\" is included " + appearances + " times! Remove duplicates and run again.");
            }
            if(!coefficients().containsKey(fixedCoeff)) {
                throw new RuntimeException("Fixed coefficient \"" + fixedCoeff + "\" not in coefficients map!");
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
        availability = computeAvailability();

        logger.info("Initialised utility function with " + allCoeffs + " coefficients " +
                "(" + fixedCoeffs.size() + " fixed).");
    }


    protected void initialiseDynamicUtilDeriv() {
        this.dynamicComponent = dynamic();
        this.utilityVector = utility();
        this.derivativeMatrix = createDerivativeMatrix();
    }

    protected abstract LinkedHashMap<String,Double> coefficients();

    protected abstract List<String> fixed();

    protected abstract UtilityFunction[] utility();

    protected abstract Map<String, UtilityFunction[]> derivatives();

    protected DynamicComponent dynamic() {
        return null;
    }

    protected List<String> availability() {
        return new ArrayList<>();
    }

    private UtilityFunction[][] createDerivativeMatrix() {
        Map<String, UtilityFunction[]> derivativesMap = derivatives();
        UtilityFunction[][] result = new UtilityFunction[choiceCount][allCoeffs];
        for(Map.Entry<String, UtilityFunction[]> e : derivativesMap.entrySet()) {
            int coeffPos = coefficients.get(e.getKey());
            if(e.getValue().length != choiceCount) {
                throw new RuntimeException("Incorrect number of derivatives given for coefficient: " + e.getKey());
            }
            for(int k = 0 ; k < choiceCount ; k++) {
                result[k][coeffPos] = e.getValue()[k];
            }
        }
        return result;
    }

    public double getUtility(int i, int choiceIdx, double[] coeffs) {
        if(availability[choiceIdx][i]) {
            return utilityVector[choiceIdx].applyAsDouble(coeffs,i);//utility(i, choiceSet.valueOf(choiceIdx),c);
        } else {
            return Double.NEGATIVE_INFINITY;
        }
    }

    public DynamicComponent getDynamicComponent() {
        return this.dynamicComponent;
    }

    protected double beta(double[] c, String name) {
        return c[coefficients.get(name)];
    }

    public double value(int i, String col) {
        return db.getValue(i,col);
    }

    public double getDerivative(int i,int choiceIdx,double[] coeffs, int coeffIdx) {
        if(availability[choiceIdx][i]) {
            return derivativeMatrix[choiceIdx][coeffSet.valueOf(coeffIdx)].applyAsDouble(coeffs,i);
        } else {
            return 0;
        }
    }

    public int variableCoefficientCount() {
        return varCoeffs;
    }

    public String[] getChoiceNames() {
        return choiceNames;
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
    private boolean[][] computeAvailability() {
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

    public double[] contractCoeffs(double[] wFull) {
        assert allCoeffs == wFull.length;
        double[] w = new double[varCoeffs];
        for(int i = 0 ; i < allCoeffs ; i++) {
            if(!fixed[i]) {
                w[coeffSet.indexOf(i)] = wFull[i];
            } else {
                if(starting[i] != wFull[i]) {
                    String coeffName = (new ArrayList<>(getCoeffNames())).get(i);
                    throw new RuntimeException("Fixed coefficient \"" + coeffName + " has different starting value " +
                            "in specification (" + starting[i] + ") than in current coefficient array! (" + wFull[i] + ")");
                }
            }
        }
        return w;
    }

    public String[] expand(double[] z) {
        String[] result = new String[allCoeffs];
        for(int i = 0 ; i < allCoeffs ; i++) {
            if(z == null || fixed[i]) {
                result[i] = "";
            } else {
                result[i] = String.valueOf(z[coeffSet.indexOf(i)]);
            }
        }
        return result;
    }


    public String[] expand(double[] z, String format) {
        String[] result = new String[allCoeffs];
        for(int i = 0 ; i < allCoeffs ; i++) {
            if(z == null || fixed[i]) {
                result[i] = "";
            } else {
                result[i] = String.format(format,z[coeffSet.indexOf(i)]);
            }
        }
        return result;
    }


    public String[] expand(Object[] z) {
        String[] result = new String[allCoeffs];
        for(int i = 0 ; i < allCoeffs ; i++) {
            if(z == null || fixed[i]) {
                result[i] = "";
            } else {
                result[i] = String.valueOf(z[coeffSet.indexOf(i)]);
            }
        }
        return result;
    }

    protected class UtilitiesBuilder {

        private final UtilityFunction[] utilities;

        public UtilitiesBuilder() {utilities = new UtilityFunction[choiceCount];}

        public void put(int choice, UtilityFunction fn) {
            utilities[choiceSet.indexOf(choice)] = fn;
        }

        public UtilityFunction[] build() {
            return utilities;
        }

    }

    protected class DerivativesBuilder {

        Map<String, UtilityFunction[]> derivativesMap;

        public DerivativesBuilder() {
            derivativesMap = new HashMap<>();
        }

        public void put(String name, UtilityFunction... derivatives) {
            checkName(name);
            if(derivatives.length != choiceCount) {
                throw new RuntimeException("Incorrect number of derivatives entered for coefficient: " + name);
            }
            derivativesMap.put(name,derivatives);
        }

        public void putAt(String name, UtilityFunction derivative, int... choices) {
            checkName(name);
            UtilityFunction[] derivatives = new UtilityFunction[choiceCount];
            for(int i = 0 ; i < choiceCount ; i++) {
                if(ArrayUtils.contains(choices,choiceSet.valueOf(i))) {
                    derivatives[i] = derivative;
                } else {
                    derivatives[i] = (a,b) -> 0;
                }
            }
            derivativesMap.put(name,derivatives);
        }

        public void putAt(String name, double derivative, int... choices) {
            checkName(name);
            UtilityFunction[] derivatives = new UtilityFunction[choiceCount];
            for(int i = 0 ; i < choiceCount ; i++) {
                if(ArrayUtils.contains(choices,choiceSet.valueOf(i))) {
                    derivatives[i] = (a,b) -> derivative;
                } else {
                    derivatives[i] = (a,b) -> 0;
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

        public Map<String, UtilityFunction[]> build() {
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

    public int getCoeffPos(String coeffName) {
        return coefficients.get(coeffName);
    }
}
