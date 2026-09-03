package estimation;

import estimation.specifications.AbstractModelSpecification;
import io.ioUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class CoefficientsIO {

    private final static Logger logger = Logger.getLogger(CoefficientsIO.class);
    private final static String SEP = ",";

    static void print(AbstractModelSpecification u, BFGS.Results results,double ll0, double[] se, double[] t, double[] pVal, String[] sig, String filePath) {

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(filePath),false);
        assert out != null;

        logger.info("ESTIMATION RESULTS AFTER " + results.iterations + " ITERATIONS:");
        Iterator<String> coeffNames = u.getCoeffNames().iterator();
        double[] finalResults = u.expandCoeffs(results.xAtEachIteration.get(results.iterations));
        String[] seAll  = u.expand(se,"%.5f");
        String[] tAll = u.expand(t,"% .5f");
        String[] pAll = u.expand(pVal,"%.5f");
        String[] sigAll = u.expand(sig);

        String header = String.format("| %-30s | %-10s | %-7s |  %-10s |  %-10s |%n","COEFFICIENT NAME","VALUE","STD.ERR","T.TEST","P.VAL");
        out.print(header);
        System.out.print(header);
        int i = 0;
        while(coeffNames.hasNext()) {
            String line = String.format("| %-30s | % .7f | %-7s |  %-10s | %-7s %-3s |%n",coeffNames.next(),finalResults[i],seAll[i],tAll[i],pAll[i],sigAll[i]);
            out.print(line);
            System.out.print(line);
            i++;
        }

        // Print key data
        out.println();
        out.println();
        out.println("Iterations: " + results.iterations);
        out.println();
        out.println("LL0 = " + ll0);
        out.println("Start LL = " + results.llStart);
        out.println("Final LL = " + results.llOut);

        // Print stats from latent component (if applicable)
        if(u.getLatentUtilityComponent() != null) {
            out.println();
            out.println(u.getLatentUtilityComponent().getStats());
        }

        out.close();
        logger.info("Wrote these results to " + filePath);
    }

    // Write results to csv file
    static void write(AbstractModelSpecification u, BFGS.Results results, double[] se, double[] t, double[] pVal, String[] sig, String filePath) {

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(filePath),false);
        assert out != null;

        out.println("Iteration" + SEP + "LL" + SEP + String.join(SEP,u.getCoeffNames()));
        for(int i = 0 ; i <= results.iterations ; i++) {
            out.println(i + SEP + results.lAtEachIteration.get(i) + SEP + Arrays.stream(u.expandCoeffs(results.xAtEachIteration.get(i))).mapToObj(String::valueOf).collect(Collectors.joining(SEP)));
        }

        if(se != null) {
            out.println("std.err" + SEP + SEP + String.join(SEP,u.expand(se)));
        }
        if(t != null) {
            out.println("t.test" + SEP + SEP + String.join(SEP,u.expand(t)));
        }
        if(pVal != null) {
            out.println("p.val" + SEP + SEP + String.join(SEP,u.expand(pVal)));
        }
//        if(sig != null) {
//            out.println("sig" + SEP + SEP + String.join(SEP,u.expand(sig)));
//        }

        out.close();
        logger.info("Wrote coefficients at each iteration to " + filePath);
    }

    static double[] read(AbstractModelSpecification u, File file, boolean requireExactMatch) throws IOException {

        String recString;
        BufferedReader in = new BufferedReader(new FileReader(file));

        // Read header & check it matches specification
        recString = in.readLine();
        String[] header = recString.split(SEP);
        Set<String> coeffNames = u.getCoeffNames();
        int modelCoeffCount = coeffNames.size();

        if(requireExactMatch && (modelCoeffCount != header.length - 2)) {
            throw new RuntimeException("Number of coefficients in model specification (" + modelCoeffCount +
                    ") doesn't match number of coefficients in input file (" + (header.length - 2) + ")");
        }

        // Index coefficient names
        Set<String> remainingCoeffNames = new HashSet<>(coeffNames);
        int[] input2model = new int[header.length];
        Arrays.fill(input2model,-1);
        for(int i = 2 ; i < header.length ; i++) {
            String inputName = header[i];
            Integer position = u.getCoeffPos(inputName);
            if (position == null) {
                String message = "Input coefficient name not found in model specification: " + inputName;
                if(requireExactMatch) {
                    throw new RuntimeException(message);
                } else {
                    logger.warn(message);
                }
            } else {
                input2model[i] = position;
                remainingCoeffNames.remove(inputName);
            }
        }

        // Check for missing coefficient names
        if (!remainingCoeffNames.isEmpty()) {
            String message = "Some model coefficients were not in input file: " + String.join(", ", remainingCoeffNames);
            if(requireExactMatch) {
                throw new RuntimeException(message);
            } else {
                logger.warn(message);
            }
        }

        // Go to final iteration
        String finalIterationString = null;
        while((recString = in.readLine()).matches("^[0-9].*$")) {
            finalIterationString = recString;
        }
        assert finalIterationString != null;
        String[] finalIteration = finalIterationString.split(SEP);

        // Retrieve values
        int iteration = Integer.parseInt(finalIteration[0]);
        double ll = Double.parseDouble(finalIteration[1]);

        // Initialise model coefficients from starting values
        double[] modelCoefficients = u.expandCoeffs(u.getStarting());

        // Print output
        logger.info("Final iteration: " + iteration);
        System.out.printf("| %-15s | %-30s | %-10s |%n","MODEL INDEX","COEFFICIENT NAME","VALUE");
        for(int i = 2 ; i < header.length ; i++) {
            int modelIdx = input2model[i];
            double inputCoeff = Double.parseDouble(finalIteration[i]);
            System.out.printf("| %-15d | %-30s | % .7f |%n",modelIdx,header[i],inputCoeff);
            if (modelIdx != -1) {
                modelCoefficients[modelIdx] = inputCoeff;
            }
        }
        System.out.println("Final LL: " + ll);

        // Return model coefficients
        return(modelCoefficients);
    }
}
