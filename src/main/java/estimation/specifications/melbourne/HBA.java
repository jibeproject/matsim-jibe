package estimation.specifications.melbourne;

import estimation.LogitData;
import estimation.RouteAttribute;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import routing.Gradient;
import routing.disutility.components.LinkAmbience;
import routing.disutility.components.LinkStress;
import trip.Trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HBA extends MNL_Melbourne {

    private final static List<String> SOCIODEMOGRAPHIC_VARIABLES = List.of(
            "age_gr_under16","age_gr_40_54","age_gr_55up",
//            "age_gr_under16","age_gr_45_64","age_gr_65up",
            "female",
            "hhcars_0","hhcars_23");

    public HBA(LogitData data, Trip[] trips,
               Network netBike, Vehicle vehBike, TravelTime ttBike,
               Network netWalk, Vehicle vehWalk, TravelTime ttWalk) {
        super(data,trips,netBike,vehBike,ttBike,netWalk,vehWalk,ttWalk);
    }

    @Override
    List<String> sociodemographicVariables() {
        return SOCIODEMOGRAPHIC_VARIABLES;
    }

    List<RouteAttribute> streetEnvironmentAttributesBike() {
        List<RouteAttribute> bike = new ArrayList<>();
        bike.add(new RouteAttribute("g_bike_grad", l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.)));
//        bike.add(new RouteAttribute("g_bike_grad_c", "g_bike_grad",i -> value(i,"age_gr_under16") == 1));
//        bike.add(new RouteAttribute("g_bike_grad_f","g_bike_grad", i -> value(i,"female") == 1));
//        bike.add(new RouteAttribute("g_bike_grad_e","g_bike_grad", i -> value(i,"age_gr_55up") == 1));

//        bike.add(new RouteAttribute("g_bike_vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
//        bike.add(new RouteAttribute("g_bike_stressLink", l -> LinkStress.getStress(l,TransportMode.bike)));
//        bike.add(new RouteAttribute("g_bike_stressLink_c", "g_bike_stressLink",i -> value(i,"age_gr_under16") == 1));

//        bike.add(new RouteAttribute("g_bike_stressLink_f","g_bike_stressLink", i -> value(i,"female") == 1));
//        bike.add(new RouteAttribute("g_bike_stressLink_e","g_bike_stressLink", i -> value(i,"age_gr_55up") == 1));
        return bike;
    }

    List<RouteAttribute> streetEnvironmentAttributesWalk() {
        List<RouteAttribute> walk = new ArrayList<>();
//        walk.add(new RouteAttribute("g_walk_grad",l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.)));
//        walk.add(new RouteAttribute("g_walk_vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
//        walk.add(new RouteAttribute("g_walk_speed", l -> Math.min(1.,l.getFreespeed() / 22.35)));
//        walk.add(new RouteAttribute("g_walk_speed_c", "g_walk_speed", i -> value(i,"age_gr_under16") == 1));
//        walk.add(new RouteAttribute("g_walk_speed_e", "g_walk_speed", i -> value(i,"age_gr_55up") == 1));
//        walk.add(new RouteAttribute("g_walk_aadt", l -> Math.min(1.,((int) l.getAttributes().getAttribute("aadt")) * 0.865/6000.)));
//        walk.add(new RouteAttribute("g_walk_speed_aadt", l -> Math.min(1.,l.getFreespeed() / 22.35) * Math.min(1.,((int) l.getAttributes().getAttribute("aadt")) * 0.865/6000.)));
        return walk;
    }

    @Override
    protected List<String> fixed() {
        List<String> fixed = coefficients().keySet().stream().filter(s -> (s.contains("carD"))).collect(Collectors.toList());
        fixed.add("b_carP_age_gr_under16");
        fixed.add("b_carP_age_gr_55up");
        fixed.add("b_bike_age_gr_40_54");
        fixed.add("b_bike_hhcars_0");
        fixed.add("b_walk_hhcars_0");
//        fixed.add("g_walk_speed");
//        fixed.add("b_walk_hhcars_23");
//        fixed.add("b_bike_hhcars_23");
//        fixed.add("b_pt_hhcars_23");
//        fixed.add("b_carP_hhcars_23");
//        fixed.add("b_carP_p.age_55up");
//        fixed.add("b_walk_p.age_group_agg_40_54");
//        fixed.add("b_walk_p.age_55up");
//        fixed.add("b_carP_t.full_purpose_HBO");
//        fixed.add("b_bike_p.age_group_agg_40_69");
//        fixed.add("b_bike_hh.income_agg_high");
//        fixed.add("b_walk_hh.income_agg_high");
//        fixed.add("g_bike_vgvi");
//        fixed.add("g_bike_stressJct");
//        fixed.add("g_walk_grad");
//        fixed.add("g_walk_stressLink");
        return fixed;
    }
}
