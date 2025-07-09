package estimation;

import io.DiaryReader;
import org.opengis.referencing.FactoryException;

import java.io.IOException;

public class RunMnlManchester {

    private static final MnlWithRouting.ModelLoader MODEL = estimation.specifications.manchester.HBD::new;
    private static final boolean COMPUTE_ROUTE_DATA = true;
    private static final String LOGIT_DATA_ID_VAR  = "t.ID";
    private static final DiaryReader.IdMatcher TRIP_DATABASE_MATCHER = ((hhid, persid, tripid) -> hhid + persid + tripid);

    public static void main(String[] args) throws FactoryException, IOException {
        MnlWithRouting.run(args,
                MODEL,
                COMPUTE_ROUTE_DATA,
                LOGIT_DATA_ID_VAR,
                TRIP_DATABASE_MATCHER);
    }

}
