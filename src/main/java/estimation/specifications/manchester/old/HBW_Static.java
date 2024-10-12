package estimation.specifications.manchester.old;

import estimation.LogitData;

import java.util.List;
import java.util.stream.Collectors;

public class HBW_Static extends ManchesterStatic {

    private final static List<String> SOCIODEMOGRAPHIC_VARIABLES = List.of(
            "p.age_group_agg_15_24","p.age_group_agg_40_54","p.age_group_agg_55",
            "p.female",
            "hh.cars_gr_0","hh.cars_gr_2","hh.cars_gr_3",
            "hh.income_agg_low","hh.income_agg_high");


    public HBW_Static(LogitData data) {
        super(data);
    }

    @Override
    List<String> sociodemographicVariables() {
        return SOCIODEMOGRAPHIC_VARIABLES;
    }

    @Override
    protected List<String> fixed() {
        List<String> fixed = coefficients().keySet().stream().filter(s -> (s.contains("carD"))).collect(Collectors.toList());
        fixed.add("g_bike_stressLink_c");
        fixed.add("g_bike_stressJct");
        fixed.add("g_bike_stressJct_f");
        fixed.add("g_bike_stressJct_c");
        fixed.add("g_walk_stressLink");
        fixed.add("g_walk_stressLink_f");
        fixed.add("g_walk_stressLink_c");
        fixed.add("g_walk_stressJct_f");
        fixed.add("g_walk_stressJct_c");
        fixed.add("b_carP_hh.income_agg_low");
        fixed.add("b_carP_p.age_group_agg_55");
//        fixed.add("b_carP_t.full_purpose_HBO");
//        fixed.add("b_bike_p.age_group_agg_40_69");
//        fixed.add("b_bike_hh.income_agg_high");
//        fixed.add("b_walk_hh.income_agg_high");
        fixed.add("g_bike_vgvi");
        fixed.add("b_walk_hh.income_agg_low");
//        fixed.add("g_bike_stressJct");
        fixed.add("g_walk_grad");
//        fixed.add("g_walk_stressLink");
        return fixed;
    }
}
