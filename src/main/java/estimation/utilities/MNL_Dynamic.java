package estimation.utilities;

import estimation.*;
import estimation.dynamic.DynamicRouter;
import estimation.dynamic.DynamicUtilityComponent;
import io.ioUtils;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import trip.Trip;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class MNL_Dynamic extends AbstractUtilitySpecification {

    private final static List<String> MODES = List.of("carD","carP","pt","bike","walk");

    private final static List<String> SOCIODEMOGRAPHIC_VARIABLES = List.of(
            "p.age_group_agg_5_14","p.age_group_agg_15_24","p.age_group_agg_40_69","p.age_group_agg_70",
            "p.female_FALSE",
            "p.occupation_worker","p.occupation_student",
//            "hh.cars_gr_0","hh.cars_gr_2","hh.cars_gr_3",
            "hh.income_agg_low","hh.income_agg_high");

    private final DynamicRouter dynamicBike;
    private final DynamicRouter dynamicWalk;

    public MNL_Dynamic(LogitData data, Trip[] trips, Set<SimpleFeature> OAs,
                       Network netBike, Vehicle vehBike, TravelTime ttBike,
                       Network netWalk, Vehicle vehWalk, TravelTime ttWalk) {
        super(data,0,1,2,3,4);
        dynamicBike = new DynamicRouter(trips,this, TransportMode.bike,OAs,netBike,vehBike,ttBike,
                "g_bike_grad","g_bike_vgvi","g_bike_stressLink","g_bike_stressJct");
        dynamicWalk = new DynamicRouter(trips,this, TransportMode.walk,OAs,netWalk,vehWalk,ttWalk,
                "g_walk_grad","g_walk_vgvi","g_walk_stressLink","g_walk_stressJct");
//        printTravelTimes(data,"activeDataStatic.csv");
//        System.exit(-1);
        initialise();
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
        coeffs.put("b_bike_cost",-.1);
        coeffs.put("b_walk_cost",-.1);

        for(String activeMode : List.of("walk","bike")) {
            for(String routeVar : List.of("grad","vgvi","stressLink","stressJct")) {
                coeffs.put("g_" + activeMode + "_" + routeVar,0.);
            }
        }
        return coeffs;
    }

    @Override
    List<String> fixed() {
        List<String> fixed = coefficients().keySet().stream().filter(s -> (s.contains("carD"))).collect(Collectors.toList());
        fixed.add("b_carP_p.age_group_agg_5_14");
        fixed.add("b_carP_hh.income_agg_low");
        fixed.add("b_bike_hh.income_agg_high");
        fixed.add("b_walk_hh.income_agg_high");
//        fixed.add("g_bike_vgvi");
        fixed.add("g_bike_stressJct");
        fixed.add("g_walk_grad");
        fixed.add("g_walk_stressLink");
        return fixed;
    }

    // AVAILABILITY
    @Override
    List<String> availability() {
        return List.of("av_carD","av_carP","av_pt","av_bike","av_walk");
    }

    // UTILITY
    @Override
    UtilityFunction[] utility() {
        UtilitiesBuilder builder = new UtilitiesBuilder();
        builder.put(0,(c,i) -> sociodemographicUtility(c,i,"carD") + beta(c,"b_car_time") * value(i,"car_time"));
        builder.put(1,(c,i) -> sociodemographicUtility(c,i,"carP") + beta(c,"b_car_time") * value(i,"car_time"));
        builder.put(2,(c,i) -> sociodemographicUtility(c,i,"pt") + beta(c,"b_pt_time") * value(i,"pt_time"));
        builder.put(3,(c,i) -> sociodemographicUtility(c,i,"bike") + beta(c,"b_bike_cost") * (dynamicBike.getTime(i) +
                beta(c,"g_bike_grad") * dynamicBike.getGrad(i) +
                beta(c,"g_bike_vgvi") * dynamicBike.getVgvi(i) +
                beta(c,"g_bike_stressLink") * dynamicBike.getStressLink(i) +
                beta(c,"g_bike_stressJct") * dynamicBike.getStressJct(i)));
        builder.put(4,(c,i) -> sociodemographicUtility(c,i,"walk") + beta(c,"b_walk_cost") * (dynamicWalk.getTime(i) +
                beta(c,"g_walk_grad") * dynamicWalk.getGrad(i) +
                beta(c,"g_walk_vgvi") * dynamicWalk.getVgvi(i) +
                beta(c,"g_walk_stressLink") * dynamicWalk.getStressLink(i) +
                beta(c,"g_walk_stressJct") * dynamicWalk.getStressJct(i)));
        return builder.build();
    }


    // Sociodemographic part of utility
    private double sociodemographicUtility(double[] c, int i, String mode) {
        double result = beta(c,"asc_" + mode);
        for(String sd : SOCIODEMOGRAPHIC_VARIABLES) {
            result += beta(c,"b_" + mode + "_" + sd) * value(i,sd);
        }
        return result;
    }

    // DERIVATIVES
    @Override
    Map<String, UtilityFunction[]> derivatives() {
        DerivativesBuilder builder = new DerivativesBuilder();

        // Alternative-specific constants
        builder.putAt("asc_carD", 1,0);
        builder.putAt("asc_carP", 1,1);
        builder.putAt("asc_pt",   1,2);
        builder.putAt("asc_bike", 1,3);
        builder.putAt("asc_walk", 1,4);

        // Sociodemographic variables
        for(String sd : SOCIODEMOGRAPHIC_VARIABLES) {
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
        builder.putAt("b_bike_cost",(c,i)->dynamicBike.getTime(i) +
                beta(c,"g_bike_grad") * dynamicBike.getGrad(i) +
                beta(c,"g_bike_vgvi") * dynamicBike.getVgvi(i) +
                beta(c,"g_bike_stressLink") * dynamicBike.getStressLink(i) +
                beta(c,"g_bike_stressJct") * dynamicBike.getStressJct(i),3);
        builder.putAt("g_bike_grad",(c,i)->beta(c,"b_bike_cost") * dynamicBike.getGrad(i),3);
        builder.putAt("g_bike_vgvi",(c,i)->beta(c,"b_bike_cost") * dynamicBike.getVgvi(i),3);
        builder.putAt("g_bike_stressLink",(c,i)->beta(c,"b_bike_cost") * dynamicBike.getStressLink(i),3);
        builder.putAt("g_bike_stressJct",(c,i)->beta(c,"b_bike_cost") * dynamicBike.getStressJct(i),3);

        // Walk
        builder.putAt("b_walk_cost",(c,i)->dynamicWalk.getTime(i) +
                beta(c,"g_walk_grad") * dynamicWalk.getGrad(i) +
                beta(c,"g_walk_vgvi") * dynamicWalk.getVgvi(i) +
                beta(c,"g_walk_stressLink") * dynamicWalk.getStressLink(i) +
                beta(c,"g_walk_stressJct") * dynamicWalk.getStressJct(i),4);
        builder.putAt("g_walk_grad",(c,i)->beta(c,"b_walk_cost") * dynamicWalk.getGrad(i),4);
        builder.putAt("g_walk_vgvi",(c,i)->beta(c,"b_walk_cost") * dynamicWalk.getVgvi(i),4);
        builder.putAt("g_walk_stressLink",(c,i)->beta(c,"b_walk_cost") * dynamicWalk.getStressLink(i),4);
        builder.putAt("g_walk_stressJct",(c,i)->beta(c,"b_walk_cost") * dynamicWalk.getStressJct(i),4);

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

    private void printTravelTimes(LogitData logitData, String filePath) {
        logger.info("Writing trip times to " + filePath + "...");
        
        final String SEP = ",";

        String[] ids = logitData.getIds();
        PrintWriter out = ioUtils.openFileForSequentialWriting(new File(filePath),false);
        assert out != null;
        out.println("t.ID,bike_time,bike_grad,bike_vgvi,bike_stressLink,bike_stressJct," +
                "walk_time,walk_grad,walk_vgvi,walk_stressLink,walk_stressJct");
        for(int i = 0 ; i < ids.length ; i++) {
            String line = ids[i] +
                    SEP + dynamicBike.getTime(i) +
                    SEP + dynamicBike.getGrad(i) +
                    SEP + dynamicBike.getVgvi(i) +
                    SEP + dynamicBike.getStressLink(i) +
                    SEP + dynamicBike.getStressJct(i) +
                    SEP + dynamicWalk.getTime(i) +
                    SEP + dynamicWalk.getGrad(i) +
                    SEP + dynamicWalk.getVgvi(i) +
                    SEP + dynamicWalk.getStressLink(i) +
                    SEP + dynamicWalk.getStressJct(i);
            out.println(line);
        }
        out.close();
    }
}
