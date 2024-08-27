package estimation.utilities;

import estimation.*;
import estimation.dynamic.DynamicRouter;
import estimation.dynamic.DynamicUtilityComponent;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import trip.Trip;

import java.util.*;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;

public class MNL_Dynamic extends AbstractUtilityFunction {

    private final static List<String> MODES = List.of("carD","carP","pt","bike","walk");

    private final static List<String> SOCIODEMOGRAPHIC_VARIABLES = List.of(
            "p.age_group_agg_5_14","p.age_group_agg_15_24","p.age_group_agg_40_69","p.age_group_agg_70",
            "p.female_FALSE",
            "p.occupation_worker","p.occupation_student",
            "hh.cars_gr_0","hh.cars_gr_1","hh.cars_gr_2","hh.cars_gr_3",
            "hh.income_agg_low","hh.income_agg_medium","hh.income_agg_high");

    private final DynamicRouter dynamicBike;
    private final DynamicRouter dynamicWalk;

    public MNL_Dynamic(LogitData data, Trip[] trips, Set<SimpleFeature> OAs,
                       Network netBike, Vehicle vehBike, TravelTime ttBike,
                       Network netWalk, Vehicle vehWalk, TravelTime ttWalk) {
        super(data,0,1,2,3,4);
        dynamicBike = new DynamicRouter(trips,this, TransportMode.bike,OAs,netBike,vehBike,ttBike,
                "b_bike_gc","g_bike_grad","g_bike_vgvi","g_bike_stressLink","g_bike_stressJct");
        dynamicWalk = new DynamicRouter(trips,this, TransportMode.walk,OAs,netWalk,vehWalk,ttWalk,
                "b_walk_gc","g_walk_grad","g_walk_vgvi","g_walk_stressLink","g_walk_stressJct");
    }

    @Override
    DynamicUtilityComponent dynamic() {
        return new DynamicActive(dynamicBike, dynamicWalk);
    }

    @Override
    LinkedHashMap<String, Double> coefficients() {
        LinkedHashMap<String,Double> coeffs = new LinkedHashMap<>();

        for(String mode : MODES) {
            coeffs.put("asc_" + mode, 0.);
            for(String sd : SOCIODEMOGRAPHIC_VARIABLES) {
                coeffs.put("b_" + mode + "_" + sd,0.);
            }
        }
        coeffs.put("b_car_time",0.);
        coeffs.put("b_pt_time",0.);
        coeffs.put("b_bike_gc",0.);
        coeffs.put("b_walk_gc",0.);

        for(String activeMode : List.of("walk","bike")) {
            for(String routeVar : List.of("grad","vgvi","stressLink","stressJct")) {
                coeffs.put("g_" + activeMode + "_" + routeVar,0.);
            }
        }
        return coeffs;
    }

    @Override
    List<String> fixed() {
        return coefficients().keySet().stream().filter(s -> s.contains("carD")).collect(Collectors.toList());
    }

    // AVAILABILITY
    @Override
    List<String> availability() {
        return List.of("av_carD","av_carP","av_pt","av_bike","av_walk");
    }

    // UTILITY
    @Override
    double utility(int i, int choice, double[] c) {
        switch(choice) {
            case 0: return sociodemographicUtility(c,i,"carD") + beta(c,"b_car_time") * value(i,"car_time");
            case 1: return sociodemographicUtility(c,i,"carP") + beta(c,"b_car_time") * value(i,"car_time");
            case 2: return sociodemographicUtility(c,i,"pt") + beta(c,"b_pt_time") * value(i,"pt_time");
            case 3: return sociodemographicUtility(c,i,"bike") + beta(c,"b_bike_gc") * dynamicBike.getCost(i);
            case 4: return sociodemographicUtility(c,i,"walk") + beta(c,"b_walk_gc") * dynamicWalk.getCost(i);
            default: throw new RuntimeException("Unknown choice " + choice);
        }
    }

    // Sociodemographic part of utility
    double sociodemographicUtility(double[] c, int i, String mode) {
        double result = beta(c,"asc_" + mode);
        for(String sd : SOCIODEMOGRAPHIC_VARIABLES) {
            result += beta(c,"b_" + mode + "_" + sd) * value(i,sd);
        }
        return result;
    }

    // DERIVATIVES
    @Override
    Map<String,IntToDoubleFunction[]> derivatives() {
        DerivativesBuilder builder = new DerivativesBuilder();

        // Alternative-specific constants
        builder.putAt("asc_carD", i->1,0);
        builder.putAt("asc_carP", i->1,1);
        builder.putAt("asc_pt",   i->1,2);
        builder.putAt("asc_bike", i->1,3);
        builder.putAt("asc_walk", i->1,4);

        // Sociodemographic variables
        for(String sd : SOCIODEMOGRAPHIC_VARIABLES) {
            builder.putAt("b_carD_" + sd,i->value(i,sd),0);
            builder.putAt("b_carP_" + sd,i->value(i,sd),1);
            builder.putAt("b_pt_"   + sd,i->value(i,sd),2);
            builder.putAt("b_bike_" + sd,i->value(i,sd),3);
            builder.putAt("b_walk_" + sd,i->value(i,sd),4);
        }

        // Travel time (for car and pt)
        builder.putAt("b_car_time",i->value(i,"car_time"),0,1);
        builder.putAt("b_pt_time", i->value(i,"pt_time"),2);

        // Bike
        builder.putAt("b_bike_gc",dynamicBike::getCost,3);
        builder.putAt("g_bike_grad",dynamicBike::getDerivGammaGrad,3);
        builder.putAt("g_bike_vgvi",dynamicBike::getDerivGammaVgvi,3);
        builder.putAt("g_bike_stressLink",dynamicBike::getDerivGammaStressLink,3);
        builder.putAt("g_bike_stressJct",dynamicBike::getDerivGammaStressJct,3);

        // Walk
        builder.putAt("b_walk_gc",dynamicWalk::getCost,4);
        builder.putAt("g_walk_grad",dynamicWalk::getDerivGammaGrad,4);
        builder.putAt("g_walk_vgvi",dynamicWalk::getDerivGammaVgvi,4);
        builder.putAt("g_walk_stressLink",dynamicWalk::getDerivGammaStressLink,4);
        builder.putAt("g_walk_stressJct",dynamicWalk::getDerivGammaStressJct,4);

        // Return
        return builder.build();
    }

    private static class DynamicActive implements DynamicUtilityComponent {

        final DynamicRouter dynamicBike;
        final DynamicRouter dynamicWalk;

        private DynamicActive(DynamicRouter dynamicBike, DynamicRouter dynamicWalk) {
            this.dynamicBike = dynamicBike;
            this.dynamicWalk = dynamicWalk;
        }

        @Override
        public void update(double[] x) {
            dynamicBike.update(x);
            dynamicWalk.update(x);
        }
    }
}
