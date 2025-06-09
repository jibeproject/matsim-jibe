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

public class NHBW extends MNL_Melbourne {

    private final static List<String> SOCIODEMOGRAPHIC_VARIABLES = List.of(
            "age_gr_under25","age_gr_40_54","age_gr_55up",
            "female",
            "hhcars_0","hhcars_2","hhcars_3","hhcars_23");

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
        bike.add(new RouteAttribute("g_bike_stressLink", l -> LinkStress.getStress(l,TransportMode.bike)));
        return bike;
    }

    List<RouteAttribute> streetEnvironmentAttributesWalk() {
        List<RouteAttribute> walk = new ArrayList<>();
//        walk.add(new RouteAttribute("g_walk_grad", l -> Math.max(Math.min(Gradient.getGradient(l),0.5),0.)));
//        walk.add(new RouteAttribute("g_walk_vgvi", l -> Math.max(0.,0.81 - LinkAmbience.getVgviFactor(l))));
        walk.add(new RouteAttribute("g_walk_speed", l -> Math.min(1.,l.getFreespeed() / 22.35)));
        return walk;
    }

    @Override
    protected List<String> fixed() {
        List<String> fixed = coefficients().keySet().stream().filter(s -> (s.contains("carD"))).collect(Collectors.toList());
        fixed.add("b_bike_age_gr_under25");
        fixed.add("b_carP_hhcars_23");
        fixed.add("b_pt_hhcars_23");
        fixed.add("b_bike_hhcars_23");
        fixed.add("b_walk_hhcars_0");
        fixed.add("b_walk_hhcars_2");
        fixed.add("b_walk_hhcars_3");
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
