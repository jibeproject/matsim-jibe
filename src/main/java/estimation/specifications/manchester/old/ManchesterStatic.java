package estimation.specifications.manchester.old;

import estimation.LogitData;
import estimation.UtilityFunction;
import estimation.specifications.AbstractModelSpecification;

import java.util.*;

public abstract class ManchesterStatic extends AbstractModelSpecification {

    private final static List<String> MODES = List.of("carD","carP","pt","bike","walk");

    ManchesterStatic(LogitData data) {
        super(data,true,0,1,2,3,4);
    }

    abstract List<String> sociodemographicVariables();

    @Override
    protected LinkedHashMap<String, Double> coefficients() {
        LinkedHashMap<String,Double> coeffs = new LinkedHashMap<>();

        for(String mode : MODES) {
            coeffs.put("asc_" + mode, 0.);
            for(String sd : sociodemographicVariables()) {
                coeffs.put("b_" + mode + "_" + sd,0.);
            }
        }
        coeffs.put("b_car_time",0.);
        coeffs.put("b_pt_time",0.);
        coeffs.put("b_bike_cost",-.1);
        coeffs.put("b_walk_cost",-.1);

        for(String activeMode : List.of("bike","walk")) {
            for(String routeVar : List.of("grad","vgvi")) {
                coeffs.put("g_" + activeMode + "_" + routeVar,0.);
            }
            for(String routeVar : List.of("stressLink","stressJct")) {
                coeffs.put("g_" + activeMode + "_" + routeVar,0.);
                coeffs.put("g_" + activeMode + "_" + routeVar + "_f",0.);
                coeffs.put("g_" + activeMode + "_" + routeVar + "_c",0.);
            }
        }

        return coeffs;
    }

    // AVAILABILITY
    @Override
    protected List<String> availability() {
        return List.of("av_carD","av_carP","av_pt","av_bike","av_walk");
    }

    // UTILITY
    @Override
    protected UtilityFunction[] utility() {
        UtilitiesBuilder builder = new UtilitiesBuilder();
        builder.put(0,(c,i) -> sociodemographicUtility(c,i,"carD") + beta(c,"b_car_time") * value(i,"car_time"));
        builder.put(1,(c,i) -> sociodemographicUtility(c,i,"carP") + beta(c,"b_car_time") * value(i,"car_time"));
        builder.put(2,(c,i) -> sociodemographicUtility(c,i,"pt")   + beta(c,"b_pt_time") * value(i,"pt_time"));
        builder.put(3,(c,i) -> sociodemographicUtility(c,i,"bike") + beta(c,"b_bike_cost") * pathCostBike(c,i));
        builder.put(4,(c,i) -> sociodemographicUtility(c,i,"walk") + beta(c,"b_walk_cost") * pathCostWalk(c,i));
        return builder.build();
    }

    // Sociodemographic part of utility
    private double sociodemographicUtility(double[] c, int i, String mode) {
        double result = beta(c,"asc_" + mode);
        for(String sd : sociodemographicVariables()) {
            result += beta(c,"b_" + mode + "_" + sd) * value(i,sd);
        }
        return result;
    }

    private double pathCostBike(double[] c, int i) {
        return value(i,"bike_time") +
                beta(c,"g_bike_grad") * value(i,"bike_grad") +
                beta(c,"g_bike_vgvi") * value(i,"bike_vgvi") +
                beta(c,"g_bike_stressLink") * value(i,"bike_stressLink") +
                beta(c,"g_bike_stressLink_f") * value(i,"bike_stressLink") * value(i,"p.female") +
                beta(c,"g_bike_stressLink_c") * value(i,"bike_stressLink") * value(i,"p.age_group_agg_5_14") +
                beta(c,"g_bike_stressJct") * value(i,"bike_stressJct") +
                beta(c,"g_bike_stressJct_f") * value(i,"bike_stressJct") * value(i,"p.female") +
                beta(c,"g_bike_stressJct_c") * value(i,"bike_stressJct") * value(i,"p.age_group_agg_5_14");
    }

    private double pathCostWalk(double[] c, int i) {
        return value(i,"walk_time") +
                beta(c,"g_walk_grad") * value(i,"walk_grad") +
                beta(c,"g_walk_vgvi") * value(i,"walk_vgvi") +
                beta(c,"g_walk_stressLink") * value(i,"walk_stressLink") +
                beta(c,"g_walk_stressLink_f") * value(i,"walk_stressLink") * value(i,"p.female") +
                beta(c,"g_walk_stressLink_c") * value(i,"walk_stressLink") * value(i,"p.age_group_agg_5_14") +
                beta(c,"g_walk_stressJct") * value(i,"walk_stressJct") +
                beta(c,"g_walk_stressJct_f") * value(i,"walk_stressJct") * value(i,"p.female") +
                beta(c,"g_walk_stressJct_c") * value(i,"walk_stressJct") * value(i,"p.age_group_agg_5_14");
    }

    // DERIVATIVES
    @Override
    protected Map<String, UtilityFunction[]> derivatives() {
        DerivativesBuilder builder = new DerivativesBuilder();

        // Alternative-specific constants
        builder.putAt("asc_carD", 1,0);
        builder.putAt("asc_carP", 1,1);
        builder.putAt("asc_pt", 1,2);
        builder.putAt("asc_bike", 1,3);
        builder.putAt("asc_walk", 1,4);

        // Sociodemographic variables
        for(String sd : sociodemographicVariables()) {
            builder.putAt("b_carD_" + sd,(c,i)->value(i,sd),0);
            builder.putAt("b_carP_" + sd,(c,i)->value(i,sd),1);
            builder.putAt("b_pt_"   + sd,(c,i)->value(i,sd),2);
            builder.putAt("b_bike_" + sd,(c,i)->value(i,sd),3);
            builder.putAt("b_walk_" + sd,(c,i)->value(i,sd),4);
        }

        // Travel time (for car and pt)
        builder.putAt("b_car_time",(c,i)->value(i,"car_time"),0,1);
        builder.putAt("b_pt_time", (c,i)->value(i,"pt_time"),2);

        // Bike
        builder.putAt("b_bike_cost", this::pathCostBike,3);
        builder.putAt("g_bike_grad",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_grad"),3);
        builder.putAt("g_bike_vgvi",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_vgvi"),3);
        builder.putAt("g_bike_stressLink",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_stressLink"),3);
        builder.putAt("g_bike_stressLink_f",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_stressLink") * value(i,"p.female"),3);
        builder.putAt("g_bike_stressLink_c",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_stressLink") * value(i,"p.age_group_agg_5_14"),3);
        builder.putAt("g_bike_stressJct",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_stressJct"),3);
        builder.putAt("g_bike_stressJct_f",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_stressJct") * value(i,"p.female"),3);
        builder.putAt("g_bike_stressJct_c",(c,i)->beta(c,"b_bike_cost") * value(i,"bike_stressJct") * value(i,"p.age_group_agg_5_14"),3);


        // Walk
        builder.putAt("b_walk_cost",this::pathCostWalk,4);
        builder.putAt("g_walk_grad",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_grad"),4);
        builder.putAt("g_walk_vgvi",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_vgvi"),4);
        builder.putAt("g_walk_stressLink",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_stressLink"),4);
        builder.putAt("g_walk_stressLink_f",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_stressLink") * value(i,"p.female"),4);
        builder.putAt("g_walk_stressLink_c",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_stressLink") * value(i,"p.age_group_agg_5_14"),4);
        builder.putAt("g_walk_stressJct",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_stressJct"),4);
        builder.putAt("g_walk_stressJct_f",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_stressJct") * value(i,"p.female"),4);
        builder.putAt("g_walk_stressJct_c",(c,i)->beta(c,"b_walk_cost") * value(i,"walk_stressJct") * value(i,"p.age_group_agg_5_14"),4);

        // Return
        return builder.build();
    }
}
