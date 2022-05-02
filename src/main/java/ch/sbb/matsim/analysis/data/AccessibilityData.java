package ch.sbb.matsim.analysis.data;

import java.util.*;

public class AccessibilityData<T> {

    public final Map<T, Integer> zone2index;
    public final double[] distData;
    //private final float[] timeData;
    public final ArrayList<double[]> attrData = new ArrayList<>();


    public AccessibilityData(Set<T> zones, int attributeCount) {
        int size = zones.size();
        this.zone2index = new HashMap<>((int) (size * 1.5));
        int index = 0;
        for(T z : zones) {
            this.zone2index.put(z, index);
            index++;
        }
        this.distData = new double[size];
        //this.timeData = new float[size];
        for(int i = 0 ; i < attributeCount ; i++) {
            this.attrData.add(i,new double[size]);
        }
    }

/*    public void addTime(T zone, float value) {
        this.timeData[this.zone2index.get(zone)] += value;
    }*/

    public void addDist(T zone, double value) {
        this.distData[this.zone2index.get(zone)] += value;
    }

    public void addAttr(T zone, int attr, double value) {
        this.attrData.get(attr)[this.zone2index.get(zone)] += value;
    }

   /* public float getTime(T zone) {
        return this.timeData[this.zone2index.get(zone)];
    }*/

    public double getDist(T zone) {
        return this.distData[this.zone2index.get(zone)];
    }

    public double getAttr(T zone, int attr){
        return this.attrData.get(attr)[this.zone2index.get(zone)];
    }

}