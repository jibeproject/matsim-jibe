package estimation.specifications;

import estimation.*;
import estimation.latent.*;
import gis.GisUtils;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;
import resources.Properties;
import resources.Resources;
import trip.Trip;

import java.io.IOException;
import java.util.*;

import static estimation.SoftPlusTransform.*;

public abstract class BaseModelSpecification extends AbstractModelSpecification {

    private final static String[] MODES = {"carD","carP","pt","bike","walk"};
    private final static int[] VALUES = {0,1,2,3,4};

    private final List<RouteAttribute> attributesBike;
    private final List<RouteAttribute> attributesWalk;

    private final RouteData bikeRouteData;
    private final RouteData walkRouteData;

    public BaseModelSpecification(LogitData data, Trip[] trips,
                                  Network netBike, Vehicle vehBike, TravelTime ttBike,
                                  Network netWalk, Vehicle vehWalk, TravelTime ttWalk) {
        super(data,false,MODES,VALUES);
        attributesBike = streetEnvironmentAttributesBike();
        attributesWalk = streetEnvironmentAttributesWalk();
        initialiseCoeffAvail();
        if(netBike == null || netWalk == null) {
            bikeRouteData = new StaticRouter(data.getIds(), attributesBike, TransportMode.bike);
            walkRouteData = new StaticRouter(data.getIds(), attributesWalk, TransportMode.walk);
        } else {
            Set<SimpleFeature> zones = null;
            String zoneSystemFilename = Resources.instance.getString(Properties.ZONE_SYSTEM);
            String zoneId = Resources.instance.getString(Properties.ZONE_ID);
            if (zoneSystemFilename != null && zoneId != null) {
                try {
                    zones = GisUtils.readGpkg(zoneSystemFilename);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            bikeRouteData = new LatentRouter(data.getIds(), trips,this, TransportMode.bike,zones,zoneId,netBike,vehBike,ttBike,attributesBike);
            walkRouteData = new LatentRouter(data.getIds(), trips,this, TransportMode.walk,zones,zoneId,netWalk,vehWalk,ttWalk,attributesWalk);
        }
        initialiseLatentUtilDeriv();
    }

    protected abstract List<String> sociodemographicVariables();

    @Override
    protected LatentUtilityComponent latent() {
        if(bikeRouteData instanceof LatentUtilityComponent && walkRouteData instanceof LatentUtilityComponent) {
            return new LatentUtilityActive((LatentUtilityComponent) bikeRouteData, (LatentUtilityComponent) walkRouteData);
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
            coeffs.put(attribute.getName(),0.);
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
        builder.put(0,(c,i) -> sociodemographicUtility(c,i,"carD") + beta(c,"b_car_time") * value(i,"car_time"));
        builder.put(1,(c,i) -> sociodemographicUtility(c,i,"carP") + beta(c,"b_car_time") * value(i,"car_time"));
        builder.put(2,(c,i) -> sociodemographicUtility(c,i,"pt") + beta(c,"b_pt_time") * value(i,"pt_time"));
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

    public UtilityFunction[] marginalCost() {
        UtilitiesBuilder builder = new UtilitiesBuilder();
        builder.put(0, (c, i) -> 1.);
        builder.put(1, (c, i) -> 1.);
        builder.put(2, (c, i) -> 1.);
        builder.put(3, (c, i) -> pathCostBike(c,i) / bikeRouteData.getTime(i));
        builder.put(4, (c, i) -> pathCostWalk(c,i) / walkRouteData.getTime(i));
        return builder.build();
    }

    // Path cost
    private double pathCostBike(double[] c, int i) {
        double cost = bikeRouteData.getTime(i);
        for(int j = 0 ; j < attributesBike.size() ; j++) {
            cost += transform(beta(c,attributesBike.get(j).getName())) * bikeRouteData.getAttribute(i,j);
        }
        return cost;
    }

    private double pathCostWalk(double[] c, int i) {
        double cost = walkRouteData.getTime(i);
        for(int j = 0 ; j < attributesWalk.size() ; j++) {
            cost += transform(beta(c,attributesWalk.get(j).getName())) * walkRouteData.getAttribute(i,j);
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
        builder.putAt("b_car_time",(c,i)->value(i,"car_time"),0,1);
        builder.putAt("b_pt_time", (c,i)->value(i,"pt_time"),2);

        // Bike
        builder.putAt("b_bike_cost", this::pathCostBike,3);
        for(int j = 0 ; j < attributesBike.size() ; j++) {
            int finalJ = j;
            builder.putAt(attributesBike.get(j).getName(),(c,i)->
                    beta(c,"b_bike_cost") *
                            transformDerivative(beta(c,attributesBike.get(finalJ).getName())) *
                            bikeRouteData.getAttribute(i,finalJ),3);
        }

        // Walk
        builder.putAt("b_walk_cost", this::pathCostWalk,4);
        for(int j = 0 ; j < attributesWalk.size() ; j++) {
            int finalJ = j;
            builder.putAt(attributesWalk.get(j).getName(),
                    (c,i)->beta(c,"b_walk_cost") *
                            transformDerivative(beta(c,attributesWalk.get(finalJ).getName())) *
                            walkRouteData.getAttribute(i,finalJ),4);
        }

        // Return
        return builder.build();
    }

    protected List<RouteAttribute> streetEnvironmentAttributesBike() {
        return new ArrayList<>();
    }

    protected List<RouteAttribute> streetEnvironmentAttributesWalk() {
        return new ArrayList<>();
    }

}
