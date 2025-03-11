package estimation.specifications.manchester;

import estimation.LogitData;
import estimation.RouteAttribute;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import routing.Gradient;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import trip.Trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NHBW extends MNL_Manchester {

    private final static List<String> SOCIODEMOGRAPHIC_VARIABLES = List.of(
            "p.age_group_agg_15_24",
            "p.female",
            "hh.cars_gr_0","hh.cars_gr_23",
            "hh.income_agg_high");

    public NHBW(LogitData data, Trip[] trips, Set<SimpleFeature> OAs,
                Network netBike, Vehicle vehBike, TravelTime ttBike,
                Network netWalk, Vehicle vehWalk, TravelTime ttWalk) {
        super(data,trips,OAs,netBike,vehBike,ttBike,netWalk,vehWalk,ttWalk);
    }

    @Override
    List<String> sociodemographicVariables() {
        return SOCIODEMOGRAPHIC_VARIABLES;
    }

    List<RouteAttribute> streetEnvironmentAttributesBike() {
        List<RouteAttribute> bike = new ArrayList<>();
//        bike.add(new RouteAttribute("g_bike_grad", l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.)));
//        bike.add(new RouteAttribute("g_bike_vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
//        bike.add(new RouteAttribute("g_bike_stressLink", l -> LinkStress.getStress(l, TransportMode.bike)));
        return bike;
    }

    List<RouteAttribute> streetEnvironmentAttributesWalk() {
        List<RouteAttribute> walk = new ArrayList<>();
//        bike.add(new BuiltEnvironmentAttribute("grad",l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.),p -> true));
//        walk.add(new RouteAttribute("g_walk_vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
//        walk.add(new RouteAttribute("g_walk_stressJct", l -> JctStress.getStressProp(l,TransportMode.walk)));
//        walk.add(new RouteAttribute("g_walk_speed", l -> Math.min(1.,((double) l.getAttributes().getAttribute("speedLimitMPH")) / 50.)));
//        walk.add(new RouteAttribute("g_walk_speed_c", "g_walk_speed", i -> value(i,"p.age_group_agg_5_14") == 1));
//        walk.add(new RouteAttribute("g_walk_speed_o", "g_walk_speed", i -> value(i,"p.age_65up") == 1));
//        walk.add(new RouteAttribute("g_walk_aadt", l -> Math.min(1.,((int) l.getAttributes().getAttribute("aadt")) * 0.865/6000.)));
//        walk.add(new RouteAttribute("g_walk_aadt_c", "g_walk_aadt", i -> value(i,"p.age_group_agg_5_14") == 1));
//        walk.add(new RouteAttribute("g_walk_aadt_o", "g_walk_aadt", i -> value(i,"p.age_65up") == 1));

//        walk.add(new RouteAttribute("g_walk_speed_aadt", l -> Math.min(1.,((double) l.getAttributes().getAttribute("speedLimitMPH")) / 50.) * Math.min(1.,((int) l.getAttributes().getAttribute("aadt")) * 0.865/6000.)));
        return walk;
    }

    @Override
    protected List<String> fixed() {
        List<String> fixed = coefficients().keySet().stream().filter(s -> (s.contains("carD") | s.contains("bike"))).collect(Collectors.toList());
        fixed.remove("asc_bike");
        fixed.remove("b_bike_cost");
        fixed.add("b_pt_p.female");
        fixed.add("b_carP_p.female");
        fixed.add("b_walk_p.age_group_agg_15_24");
        fixed.add("b_walk_hh.cars_gr_23");
//        fixed.add("b_carP_p.age_group_agg_5_14");
        fixed.add("b_carP_hh.income_agg_high");
//        fixed.add("b_carP_t.full_purpose_HBO");
//        fixed.add("b_bike_p.age_group_agg_40_69");
//        fixed.add("b_bike_hh.income_agg_high");
//        fixed.add("b_walk_hh.income_agg_high");
//        fixed.add("g_bike_vgvi");
//        fixed.add("g_bike_stressJct");
//        fixed.add("g_bike_stressJct_f");
//        fixed.add("g_bike_stressJct_c");
//        fixed.add("g_walk_grad");
//        fixed.add("g_walk_stressLink");
//        fixed.add("g_walk_stressLink_f");
//        fixed.add("g_walk_stressJct_f");
        return fixed;
    }
}
