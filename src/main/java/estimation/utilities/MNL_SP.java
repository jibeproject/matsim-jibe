package estimation.utilities;

import estimation.LogitData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

public class MNL_SP extends AbstractUtilityFunction {

    public MNL_SP(LogitData data) {
        super(data,1,2,3,4);
    }

    @Override
    LinkedHashMap<String,Double> coefficients() {
        LinkedHashMap<String,Double> coeffs = new LinkedHashMap<>();
        coeffs.put("asc_car",0.);
        coeffs.put("asc_bus",0.);
        coeffs.put("asc_air",0.);
        coeffs.put("asc_rail",0.);
        coeffs.put("b_tt_car",0.);
        coeffs.put("b_tt_bus",0.);
        coeffs.put("b_tt_air",0.);
        coeffs.put("b_tt_rail",0.);
        coeffs.put("b_access",0.);
        coeffs.put("b_cost",0.);
        coeffs.put("b_no_frills",0.);
        coeffs.put("b_wifi",0.);
        coeffs.put("b_food",0.);
        return coeffs;
    }

    @Override
    List<String> fixed() {
        return List.of("asc_car","b_no_frills");
    }

    // AVAILABILITY (OPTIONAL)
    @Override
    List<String> availability() {
        return List.of("av_car","av_bus","av_air","av_rail");
    }

    // UTILITY
    @Override
    double utility(int i, int choice, double[] c) {
        switch(choice) {
            case 1: return beta(c,"asc_car") +
                    beta(c,"b_tt_car") * value(i,"time_car") +
                    beta(c,"b_cost")   * value(i, "cost_car");
            case 2: return beta(c,"asc_bus") +
                    beta(c,"b_tt_bus") * value(i, "time_bus") +
                    beta(c,"b_access") * value(i, "access_bus") +
                    beta(c,"b_cost") * value(i, "cost_bus");
            case 3: return beta(c,"asc_air") +
                    beta(c,"b_tt_air") * value(i, "time_air") +
                    beta(c,"b_access") * value(i, "access_air") +
                    beta(c,"b_cost") * value(i, "cost_air") +
                    beta(c,"b_no_frills") * (value(i, "service_air") == 1 ? 1 : 0) +
                    beta(c,"b_wifi") * (value(i, "service_air") == 2 ? 1 : 0) +
                    beta(c,"b_food") * (value(i, "service_air") == 3 ? 1 : 0);
            case 4: return beta(c,"asc_rail") +
                    beta(c,"b_tt_rail") * value(i, "time_rail") +
                    beta(c,"b_access") * value(i, "access_rail") +
                    beta(c,"b_cost") * value(i, "cost_rail") +
                    beta(c,"b_no_frills") * (value(i, "service_rail") == 1 ? 1 : 0) +
                    beta(c,"b_wifi") * (value(i, "service_rail") == 2 ? 1 : 0) +
                    beta(c,"b_food") * (value(i, "service_rail") == 3 ? 1 : 0);
            default: throw new RuntimeException("Unknown choice " + choice);
        }
    }

    // DERIVATIVE OF UTILITY
    @Override
    Map<String,IntToDoubleFunction[]> derivatives() {
        DerivativesBuilder builder = new DerivativesBuilder();

        // Alternative specific constants
        builder.putAt("asc_car", i->1, 1);
        builder.putAt("asc_bus", i->1, 2);
        builder.putAt("asc_air", i->1, 3);
        builder.putAt("asc_rail", i->1, 4);

        // Travel time
        builder.putAt("b_tt_car", i->value(i,"time_car"), 1);
        builder.putAt("b_tt_bus", i->value(i, "time_bus"), 2);
        builder.putAt("b_tt_air", i->value(i, "time_air"), 3);
        builder.putAt("b_tt_rail", i->value(i, "time_rail"),4);

        // Other
        builder.put("b_access", i->0, i->value(i, "access_bus"), i->value(i,"access_air"), i-> value(i,"access_rail"));
        builder.put("b_cost", i->value(i,"cost_car"), i->value(i,"cost_bus"), i->value(i,"cost_air"), i->value(i,"cost_rail"));
        builder.put("b_no_frills", i->0, i->0, i->(value(i,"service_air") == 1 ? 1 : 0), i->(value(i,"service_rail") == 1 ? 1 : 0));
        builder.put("b_wifi", i->0, i->0, i->(value(i,"service_air") == 2 ? 1 : 0), i->(value(i,"service_rail") == 2 ? 1 : 0));
        builder.put("b_food", i->0, i->0, i->(value(i,"service_air") == 3 ? 1 : 0), i->(value(i,"service_rail") == 3 ? 1 : 0));

        // Build
        return builder.build();
    }
}   
