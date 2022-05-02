package ch.sbb.matsim.analysis.data;

import ch.sbb.matsim.analysis.matrix.FloatMatrix;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PtData<T> {

    public final Map<T, Integer> orig2index;
    public final Map<T, Integer> dest2index;

    public final FloatMatrix<T> adaptionTimeMatrix;
    public final FloatMatrix<T> frequencyMatrix;

    public final FloatMatrix<T> distanceMatrix;
    public final FloatMatrix<T> travelTimeMatrix;
    public final FloatMatrix<T> accessTimeMatrix;
    public final FloatMatrix<T> egressTimeMatrix;
    public final FloatMatrix<T> transferCountMatrix;
    public final FloatMatrix<T> trainTravelTimeShareMatrix;
    public final FloatMatrix<T> trainDistanceShareMatrix;

    public final FloatMatrix<T> dataCountMatrix; // how many values/routes were taken into account to calculate the averages

    public PtData(Set<T> origins, Set<T> destinations) {
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

        this.adaptionTimeMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.frequencyMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);

        this.distanceMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.travelTimeMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.accessTimeMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.egressTimeMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.transferCountMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.dataCountMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.trainTravelTimeShareMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
        this.trainDistanceShareMatrix = new FloatMatrix<>(origSize, destSize, orig2index, dest2index, 0);
    }
}
