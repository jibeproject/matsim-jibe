package estimation.specifications.apollo;

import estimation.LogitData;
import estimation.UtilityFunction;
import estimation.specifications.AbstractModelSpecification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MNL_SP extends AbstractModelSpecification {

    public static String[] MODES = {"car","bus","air","rail"};
    public static int[] VALUES = {1,2,3,4};

    public MNL_SP(LogitData data) {
        super(data,true,MODES,VALUES);
    }

    @Override
    protected LinkedHashMap<String,Double> coefficients() {
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
    protected List<String> fixed() {
        return List.of("asc_car","b_no_frills");
    }

    // AVAILABILITY (OPTIONAL)
    @Override
    protected List<String> availability() {
        return List.of("av_car","av_bus","av_air","av_rail");
    }

    // UTILITY
    @Override
    protected UtilityFunction[] utility() {
        UtilitiesBuilder builder = new UtilitiesBuilder();
        builder.put(1,(c,i) -> beta(c,"asc_car") +
                beta(c,"b_tt_car") * value(i,"time_car") +
                beta(c,"b_cost")   * value(i, "cost_car"));
        builder.put(2,(c,i) -> beta(c,"asc_bus") +
                beta(c,"b_tt_bus") * value(i, "time_bus") +
                beta(c,"b_access") * value(i, "access_bus") +
                beta(c,"b_cost") * value(i, "cost_bus"));
        builder.put(3,(c,i) -> beta(c,"asc_air") +
                beta(c,"b_tt_air") * value(i, "time_air") +
                beta(c,"b_access") * value(i, "access_air") +
                beta(c,"b_cost") * value(i, "cost_air") +
                beta(c,"b_no_frills") * (value(i, "service_air") == 1 ? 1 : 0) +
                beta(c,"b_wifi") * (value(i, "service_air") == 2 ? 1 : 0) +
                beta(c,"b_food") * (value(i, "service_air") == 3 ? 1 : 0));
        builder.put(4,(c,i) -> beta(c,"asc_rail") +
                beta(c,"b_tt_rail") * value(i, "time_rail") +
                beta(c,"b_access") * value(i, "access_rail") +
                beta(c,"b_cost") * value(i, "cost_rail") +
                beta(c,"b_no_frills") * (value(i, "service_rail") == 1 ? 1 : 0) +
                beta(c,"b_wifi") * (value(i, "service_rail") == 2 ? 1 : 0) +
                beta(c,"b_food") * (value(i, "service_rail") == 3 ? 1 : 0));
        return builder.build();
    }

    // DERIVATIVE OF UTILITY
    @Override
    protected Map<String, UtilityFunction[]> derivatives() {
        DerivativesBuilder builder = new DerivativesBuilder();

        // Alternative specific constants
        builder.putAt("asc_car", (c,i)->1, 1);
        builder.putAt("asc_bus", (c,i)->1, 2);
        builder.putAt("asc_air", (c,i)->1, 3);
        builder.putAt("asc_rail", (c,i)->1, 4);

        // Travel time
        builder.putAt("b_tt_car", (c,i)->value(i,"time_car"), 1);
        builder.putAt("b_tt_bus", (c,i)->value(i, "time_bus"), 2);
        builder.putAt("b_tt_air", (c,i)->value(i, "time_air"), 3);
        builder.putAt("b_tt_rail", (c,i)->value(i, "time_rail"),4);

        // Other
        builder.put("b_access", (c,i)->0, (c,i)->value(i, "access_bus"), (c,i)->value(i,"access_air"), (c,i)-> value(i,"access_rail"));
        builder.put("b_cost", (c,i)->value(i,"cost_car"), (c,i)->value(i,"cost_bus"), (c,i)->value(i,"cost_air"), (c,i)->value(i,"cost_rail"));
        builder.put("b_no_frills", (c,i)->0, (c,i)->0, (c,i)->(value(i,"service_air") == 1 ? 1 : 0), (c,i)->(value(i,"service_rail") == 1 ? 1 : 0));
        builder.put("b_wifi", (c,i)->0, (c,i)->0, (c,i)->(value(i,"service_air") == 2 ? 1 : 0), (c,i)->(value(i,"service_rail") == 2 ? 1 : 0));
        builder.put("b_food", (c,i)->0, (c,i)->0, (c,i)->(value(i,"service_air") == 3 ? 1 : 0), (c,i)->(value(i,"service_rail") == 3 ? 1 : 0));

        // Build
        return builder.build();
    }
}   
