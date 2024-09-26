package estimation.utilities;

import estimation.LogitData;

import java.util.List;
import java.util.stream.Collectors;

public class HBD_Static extends StaticModeChoice {

    private final static List<String> SOCIODEMOGRAPHIC_VARIABLES = List.of(
            "p.age_group_agg_5_14","p.age_group_agg_15_24","p.age_group_agg_40_69","p.age_group_agg_70",
            "p.female",
            "p.occupation_worker",
            "hh.cars_gr_0","hh.cars_gr_2","hh.cars_gr_3",
            "hh.income_agg_high",
            "t.full_purpose_HBR","t.full_purpose_HBO");


    public HBD_Static(LogitData data) {
        super(data);
    }

    @Override
    List<String> sociodemographicVariables() {
        return SOCIODEMOGRAPHIC_VARIABLES;
    }

    @Override
    List<String> fixed() {
        List<String> fixed = coefficients().keySet().stream().filter(s -> (s.contains("carD"))).collect(Collectors.toList());
        fixed.add("b_carP_p.age_group_agg_5_14");
        fixed.add("b_carP_hh.income_agg_high");
        fixed.add("b_carP_t.full_purpose_HBO");
        fixed.add("b_bike_p.age_group_agg_40_69");
//        fixed.add("b_bike_hh.income_agg_high");
//        fixed.add("b_walk_hh.income_agg_high");
        fixed.add("g_bike_vgvi");
        fixed.add("g_bike_stressJct");
        fixed.add("g_walk_grad");
        fixed.add("g_walk_stressLink");
        return fixed;
    }
}
