package estimation;

import estimation.specifications.manchester.discretionary;
import io.DiaryReader;
import org.opengis.referencing.FactoryException;

import java.io.IOException;

public class RunMnlWithLatentRoutes {

    private static final MnlWithRouting.ModelLoader MODEL = discretionary::new;
    private static final boolean COMPUTE_ROUTE_DATA = true;
    private static final String LOGIT_DATA_ID_VAR  = "t.ID";
    private static final DiaryReader.IdMatcher DIARY_ID_MATCHER = ((hhid, persid, tripid) -> hhid + persid + tripid);

    public static void main(String[] args) throws FactoryException, IOException {
        MnlWithRouting.run(args,
                MODEL,
                COMPUTE_ROUTE_DATA,
                LOGIT_DATA_ID_VAR,
                DIARY_ID_MATCHER);
    }

}
