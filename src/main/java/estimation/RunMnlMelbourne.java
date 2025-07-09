package estimation;

import io.DiaryReader;
import org.opengis.referencing.FactoryException;

import java.io.IOException;

public class RunMnlMelbourne {

    private static final MnlWithRouting.ModelLoader MODEL = estimation.specifications.melbourne.HBSO::new;
    private static final boolean COMPUTE_ROUTE_DATA = true;
    private static final String LOGIT_DATA_ID_VAR  = "tripid";
    private static final DiaryReader.IdMatcher TRIP_ID_MATCHER = (hhid, pid, tid) -> hhid + "P" + String.format("%02d",pid) + "T" + String.format("%02d",tid);

    public static void main(String[] args) throws FactoryException, IOException {
        MnlWithRouting.run(args,
                MODEL,
                COMPUTE_ROUTE_DATA,
                LOGIT_DATA_ID_VAR,
                TRIP_ID_MATCHER);
    }

}
