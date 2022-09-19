package ch.sbb.matsim.analysis.data;

import java.util.*;

public class AccessibilityData<T> {

    public final Map<T, Integer> zone2index;
    public final double[] costData;
    public final ArrayList<double[]> attrData = new ArrayList<>();


    public AccessibilityData(Set<T> origins, int attributeCount) {
        int size = origins.size();
        this.zone2index = new HashMap<>((int) (size * 1.5));
        int index = 0;
        for(T z : origins) {
            this.zone2index.put(z, index);
            index++;
        }
        this.costData = new double[size];
        for(int i = 0 ; i < attributeCount ; i++) {
            this.attrData.add(i,new double[size]);
        }
    }

    public void addCost(T zone, double value) {
        this.costData[this.zone2index.get(zone)] += value;
    }

    public void addAttr(T zone, int attr, double value) {
        this.attrData.get(attr)[this.zone2index.get(zone)] += value;
    }

    public double getCost(T zone) {
        return this.costData[this.zone2index.get(zone)];
    }

    public double getAttr(T zone, int attr){
        return this.attrData.get(attr)[this.zone2index.get(zone)];
    }

}