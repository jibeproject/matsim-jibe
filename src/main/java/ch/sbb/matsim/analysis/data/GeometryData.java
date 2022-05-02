package ch.sbb.matsim.analysis.data;

import ch.sbb.matsim.analysis.matrix.FloatMatrix;
import ch.sbb.matsim.analysis.matrix.ObjectMatrix;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class GeometryData<T> {

    public final Map<T, Integer> orig2index;
    public final Map<T, Integer> dest2index;
    public final ObjectMatrix<T> linksTravelled;
    public final FloatMatrix<T> travelTimeMatrix;
    public final FloatMatrix<T> distanceMatrix;
    public final FloatMatrix<T> costMatrix;
    public final LinkedHashMap<String,FloatMatrix> attributeMatrices = new LinkedHashMap<>();

    public GeometryData(Set<T> origins, Set<T> destinations, Set<String> travelAttributes) {
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

        this.linksTravelled = new ObjectMatrix<>(origSize, destSize, orig2index, dest2index);
        this.travelTimeMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.distanceMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.costMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        if(travelAttributes != null) {
            for(String attribute : travelAttributes) {
                attributeMatrices.put(attribute, new FloatMatrix(origSize, destSize, orig2index, dest2index,0));
            }
        }
    }
}
