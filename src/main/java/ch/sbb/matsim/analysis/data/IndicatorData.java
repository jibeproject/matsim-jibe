package ch.sbb.matsim.analysis.data;

import ch.sbb.matsim.analysis.matrix.FloatMatrix;
import ch.sbb.matsim.analysis.matrix.ShortMatrix;

import java.util.*;

public class IndicatorData<T> {

    public final Map<T, Integer> orig2index;
    public final Map<T, Integer> dest2index;
    public final FloatMatrix<T> travelTimeMatrix;
    public final FloatMatrix<T> distanceMatrix;
    public final FloatMatrix<T> costMatrix;
    public final ShortMatrix<T> linkCountMatrix;
    public final LinkedHashMap<String,FloatMatrix> attributeMatrices = new LinkedHashMap<>();

    public IndicatorData(Set<T> origins, Set<T> destinations, Set<String> travelAttributes) {
        int origSize = origins.size();
        int destSize = destinations.size();

        this.orig2index = new HashMap<>((int) (origSize * 1.5));
        int origIndex = 0;
        for(T o : origins) {
            this.orig2index.put(o, origIndex);
            origIndex++;
        }
        this.dest2index = new HashMap<>((int) (destSize * 1.5));
        int destIndex = 0;
        for(T d : destinations) {
            this.dest2index.put(d, destIndex);
            destIndex++;
        }
        this.travelTimeMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.distanceMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.costMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.linkCountMatrix = new ShortMatrix<>(origSize, destSize, orig2index, dest2index, (short) 0);
        if(travelAttributes != null) {
            for(String attribute : travelAttributes) {
                attributeMatrices.put(attribute, new FloatMatrix(origSize, destSize, orig2index, dest2index,0));
            }
        }
    }
}