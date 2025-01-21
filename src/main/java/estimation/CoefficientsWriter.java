package estimation;

import estimation.specifications.AbstractModelSpecification;
import io.ioUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class CoefficientsWriter {

    private final static Logger logger = Logger.getLogger(CoefficientsWriter.class);
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

        // Print stats from dynamic component (if applicable)
        if(u.getDynamicComponent() != null) {
            out.println();
            out.println(u.getDynamicComponent().getStats());
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
}
