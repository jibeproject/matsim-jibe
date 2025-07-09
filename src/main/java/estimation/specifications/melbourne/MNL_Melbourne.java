package estimation.specifications.melbourne;

import estimation.LogitData;
import estimation.RouteAttribute;
import estimation.UtilityFunction;
import estimation.dynamic.DynamicComponent;
import estimation.dynamic.RouteData;
import estimation.dynamic.RouteDataDynamic;
import estimation.dynamic.RouteDataPrecomputed;
import estimation.specifications.AbstractModelSpecification;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import trip.Trip;

import java.util.*;

public abstract class MNL_Melbourne extends AbstractModelSpecification {

    private final static String[] MODES = {"carD","carP","pt","bike","walk"};
    private final static int[] VALUES = {0,1,2,3,4};

    private final List<RouteAttribute> attributesBike;
    private final List<RouteAttribute> attributesWalk;

    private final RouteData bikeRouteData;
    private final RouteData walkRouteData;


    public MNL_Melbourne(LogitData data, Trip[] trips,
                         Network netBike, Vehicle vehBike, TravelTime ttBike,
                         Network netWalk, Vehicle vehWalk, TravelTime ttWalk)  {
        super(data,false,MODES,VALUES);
        attributesBike = streetEnvironmentAttributesBike();
        attributesWalk = streetEnvironmentAttributesWalk();
        initialiseCoeffAvail();
        if(netBike == null || netWalk == null) {
            bikeRouteData = new RouteDataPrecomputed(data.getIds(), attributesBike, TransportMode.bike);
            walkRouteData = new RouteDataPrecomputed(data.getIds(), attributesWalk, TransportMode.walk);
        } else {
            bikeRouteData = new RouteDataDynamic(data.getIds(), trips,this, TransportMode.bike,null,null,netBike,vehBike,ttBike,attributesBike);
            walkRouteData = new RouteDataDynamic(data.getIds(), trips,this, TransportMode.walk,null,null,netWalk,vehWalk,ttWalk,attributesWalk);
        }
        initialiseDynamicUtilDeriv();
    }

    abstract List<String> sociodemographicVariables();

    @Override
    protected DynamicComponent dynamic() {
        if(bikeRouteData instanceof DynamicComponent && walkRouteData instanceof DynamicComponent) {
            return new DynamicActive((DynamicComponent) bikeRouteData, (DynamicComponent) walkRouteData);
        } else {
            return null;
        }
    }

    @Override
    protected LinkedHashMap<String, Double> coefficients() {
        LinkedHashMap<String,Double> coeffs = new LinkedHashMap<>();

        for(String mode : MODES) {
            coeffs.put("asc_" + mode, 0.);
            for(String sd : sociodemographicVariables()) {
                coeffs.put("b_" + mode + "_" + sd,0.);
            }
        }

        // Time / cost coefficients
        coeffs.put("b_car_time",0.);
        coeffs.put("b_pt_time",0.);
        coeffs.put("b_bike_cost",-.1);
        coeffs.put("b_walk_cost",-.1);

        // Bike attributes
        for(RouteAttribute attribute : attributesBike) {
            double starting = 0.; // attribute.getName().equals("g_bike_grad") ? 10. : 0.;
            coeffs.put(attribute.getName(),starting);
        }

        // Walk attributes
        for(RouteAttribute attribute : attributesWalk) {
            coeffs.put(attribute.getName(),0.);
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
        builder.put(0,(c,i) -> sociodemographicUtility(c,i,"carD") + beta(c,"b_car_time") * value(i,"car_freespeed"));
        builder.put(1,(c,i) -> sociodemographicUtility(c,i,"carP") + beta(c,"b_car_time") * value(i,"car_freespeed"));
        builder.put(2,(c,i) -> sociodemographicUtility(c,i,"pt") + beta(c,"b_pt_time") * value(i,"pt"));
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

    // Path cost
    private double pathCostBike(double[] c, int i) {
        double cost = bikeRouteData.getTime(i);
        for(int j = 0 ; j < attributesBike.size() ; j++) {
            cost += beta(c,attributesBike.get(j).getName()) * bikeRouteData.getAttribute(i,j);
        }
        return cost;
    }

    private double pathCostWalk(double[] c, int i) {
        double cost = walkRouteData.getTime(i);
        for(int j = 0 ; j < attributesWalk.size() ; j++) {
            cost += beta(c,attributesWalk.get(j).getName()) * walkRouteData.getAttribute(i,j);
        }
        return cost;
    }

    // DERIVATIVES
    @Override
    protected Map<String, UtilityFunction[]> derivatives() {
        DerivativesBuilder builder = new DerivativesBuilder();

        // Alternative-specific constants
        builder.putAt("asc_carD", 1,0);
        builder.putAt("asc_carP", 1,1);
        builder.putAt("asc_pt",   1,2);
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
        builder.putAt("b_car_time",(c,i)->value(i,"car_freespeed"),0,1);
        builder.putAt("b_pt_time", (c,i)->value(i,"pt"),2);

        // Bike
        builder.putAt("b_bike_cost", this::pathCostBike,3);
        for(int j = 0 ; j < attributesBike.size() ; j++) {
            int finalJ = j;
            builder.putAt(attributesBike.get(j).getName(),(c,i)->beta(c,"b_bike_cost") * bikeRouteData.getAttribute(i,finalJ),3);
        }

        // Walk
        builder.putAt("b_walk_cost", this::pathCostWalk,4);
        for(int j = 0 ; j < attributesWalk.size() ; j++) {
            int finalJ = j;
            builder.putAt(attributesWalk.get(j).getName(),(c,i)->beta(c,"b_walk_cost") * walkRouteData.getAttribute(i,finalJ),4);
        }

        // Return
        return builder.build();
    }

    List<RouteAttribute> streetEnvironmentAttributesBike() {
        return new ArrayList<>();
    }

    List<RouteAttribute> streetEnvironmentAttributesWalk() {
        return new ArrayList<>();
    }

    private static class DynamicActive implements DynamicComponent {

        final DynamicComponent dynamicBike;
        final DynamicComponent dynamicWalk;

        private DynamicActive(DynamicComponent dynamicBike, DynamicComponent dynamicWalk) {
            this.dynamicBike = dynamicBike;
            this.dynamicWalk = dynamicWalk;
        }

        @Override
        public void update(double[] x) {
            dynamicBike.update(x);
            dynamicWalk.update(x);
        }

        @Override
        public String getStats() {
            return dynamicBike.getStats() + "\n" + dynamicWalk.getStats();
        }
    }
}
