package estimation;

import estimation.utilities.AbstractUtilityFunction;
import io.ioUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class CoefficientsWriter {

    private final static Logger logger = Logger.getLogger(CoefficientsWriter.class);
    private final static String SEP = ",";

    // Write results to csv file
    static void write(AbstractUtilityFunction u, BFGS.Results results, double[] se, double[] t, double[] pVal, String[] sig, String filePath) {

        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(filePath),false);
        assert out != null;

        out.println("Iteration" + SEP + "LL" + SEP + String.join(SEP,u.getCoeffNames()));
        for(int i = 0 ; i <= results.iterations ; i++) {
            out.println(i + SEP + results.lAtEachIteration.get(i) + SEP + Arrays.stream(u.expandCoeffs(results.xAtEachIteration.get(i))).mapToObj(String::valueOf).collect(Collectors.joining(SEP)));
        }

        out.println("std.err" + SEP + SEP + String.join(SEP,u.expand(se)));
        out.println("t.test" + SEP + SEP + String.join(SEP,u.expand(t)));
        out.println("p.val" + SEP + SEP + String.join(SEP,u.expand(pVal)));
        out.println("sig" + SEP + SEP + String.join(SEP,u.expand(sig)));

        out.close();
        logger.info("Wrote coefficients at each iteration to " + filePath);
    }
}
