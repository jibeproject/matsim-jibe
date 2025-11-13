package routing.disutility;


import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;
import routing.TravelAttribute;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom walk and bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class LaurentDisutility implements TravelDisutility {

    public static final double EBIKE_REDUCTION_PER_M = 0.3;
    public static final String SEP = ",";
    public static final String VOD_FILE = "/home/cedst/copenhagen/MNL_E_separated_no_Ebase.csv";
    private final Map<String,Double> adjustmentVoD;
    private final double avgSpeed;
    private final double cost_time_factor;
    private final boolean ebike;
    private final Network network;
    private final double[] disutilities = new double[Id.getNumberOfIds(Link.class)];

    private final static Logger logger = Logger.getLogger(LaurentDisutility.class);

    public LaurentDisutility(Network network, double avgSpeed,double cost_time_factor, boolean ebike) throws IOException {

        this.network = network;
        this.cost_time_factor = cost_time_factor;
        this.avgSpeed = avgSpeed;
        this.ebike = ebike;
        this.adjustmentVoD = readAdjustmentVoT();

        logger.info("Disutility initialized for " + (ebike ? "E-":"standard ") +
                "bike with average speed " + avgSpeed +
                ", cost factor " + cost_time_factor +
                ", and the following VoT parameters:");
        for(Map.Entry<String,Double> entry : adjustmentVoD.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        // Precalculate disutilities
        if(network != null) {
            for(Link link : network.getLinks().values()) {
                disutilities[link.getId().index()] = calculateDisutility(link);
            }
            logger.info("precalculated disutilities.");
        }
    }

    @Override
    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
        if(network != null) {
            return disutilities[link.getId().index()];
        } else {
            return calculateDisutility(link);
        }
    }

    private double calculateDisutility(Link link) {
        // VoD specification is dist * (1 + sum....)
        double adjustment = 1;

        // Network attributes
        for(Map.Entry<String,Double> entry : adjustmentVoD.entrySet()) {
            String name = entry.getKey();
            double attributeValue = (double) link.getAttributes().getAttribute(entry.getKey());
            if (name.startsWith("EE_")) {
                if(!ebike) {
                    adjustment += entry.getValue() * attributeValue / link.getLength();
                }
            } else {
                adjustment += entry.getValue() * attributeValue;
            }
        }

        // For E-bike - add constant adjustment
        if(ebike) {
            adjustment -= EBIKE_REDUCTION_PER_M;
        }

        // disutility
        return (link.getLength() / avgSpeed) * cost_time_factor * adjustment;

    }

    public LinkedHashMap<String, TravelAttribute> getTravelAttributes() {
        LinkedHashMap<String, TravelAttribute> travelAttributes = new LinkedHashMap<>();
        for(Map.Entry<String,Double> entry : adjustmentVoD.entrySet()) {
            if(entry.getKey().startsWith("EE_")) {
                travelAttributes.put(entry.getKey(),(l,tt,td) -> (1 / avgSpeed) * cost_time_factor * entry.getValue() * (double) l.getAttributes().getAttribute(entry.getKey()));
            } else {
                travelAttributes.put(entry.getKey(),(l,tt,td) -> (l.getLength() / avgSpeed) * cost_time_factor * entry.getValue() * (double) l.getAttributes().getAttribute(entry.getKey()));
            }
        }
        // Base motor energy (currently as function of link length)
        travelAttributes.put("E_motor",(l,tt,td) -> (l.getLength() / avgSpeed) * cost_time_factor * EBIKE_REDUCTION_PER_M);

        // Leftover (not used currently)
        travelAttributes.put("E_leftover", (l,tt,td) -> (1 / avgSpeed) * cost_time_factor * (double) l.getAttributes().getAttribute("E_leftover"));
        travelAttributes.put("E_leftover_pos",(l,tt,td) -> (1 / avgSpeed) * cost_time_factor * Math.max(0.,(double) l.getAttributes().getAttribute("E_leftover")));
        return travelAttributes;
    }

    private Map<String,Double> readAdjustmentVoT() throws IOException {
        BufferedReader in = IOUtils.getBufferedReader(LaurentDisutility.VOD_FILE);
        String recString = in.readLine();
        String[] header = recString.split(SEP);

        int posAttr = findPositionInArray("Parameter",header);
        int posValue = findPositionInArray("Estimate",header);
        Double lengthParameter = null;

        Map<String,Double> adjustmentVoT = new LinkedHashMap<>();
        while(true) {
            // If we get to the end without reading Ln_PS_D
            if((recString = in.readLine()) == null) {
                throw new RuntimeException("Doesn't seem to be an estimation results file.");
            }

            String[] lineElements = recString.split(SEP);
            String parameterName = lineElements[posAttr];

            // Get length parameter (should be first)
            if(parameterName.equals("length")) {
                lengthParameter = Double.parseDouble(lineElements[posValue]);
                continue;
            }

            // When we get to this line, the VoD attributes are finished
            if(parameterName.equals("Ln_PS_D")) {
                break;
            }

            // Read other parameters, divide by length parameter (should only happen after length parameter)
            assert lengthParameter != null;
            try {
                if(adjustmentVoT.put(parameterName, Double.parseDouble(lineElements[posValue]) / lengthParameter) != null) {
                    throw new RuntimeException("Duplicate adjustment factor for " + parameterName);
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
        in.close();

        return adjustmentVoT;
    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

    private static int findPositionInArray (String string, String[] array) {
        int ind = -1;
        for (int a = 0; a < array.length; a++) {
            if (array[a].equalsIgnoreCase(string)) {
                ind = a;
            }
        }
        return ind;
    }
}
