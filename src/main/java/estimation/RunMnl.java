package estimation;

import estimation.specifications.apollo.MNL_SP;
import org.apache.log4j.Logger;
import org.opengis.referencing.FactoryException;
import resources.Resources;
import smile.classification.ClassLabels;

import java.io.IOException;

public class RunMnl {

    private final static Logger logger = Logger.getLogger(RunMnl.class);

    public static void main(String[] args) throws IOException, FactoryException {

        if(args.length != 2) {
            throw new RuntimeException("Program requires 2 arguments: \n" +
                    "(0) Properties file \n" +
                    "(1) Logit data file");
        }

        Resources.initializeResources(args[0]);

        // Read in TRADS trips from CSV
        logger.info("Reading logit input data from ascii file...");

        LogitData logitData = new LogitData(args[1],"choice","ID");
        logitData.read();


        int[] y = logitData.getChoices();
        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        y = codec.y;

        System.out.println("Identified " + k + " classes.");

        MultinomialLogit.run(new MNL_SP(logitData),y,k,0,1e-10,50000,"SP_results.csv");
    }
}
