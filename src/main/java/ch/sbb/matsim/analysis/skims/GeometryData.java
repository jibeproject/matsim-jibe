package ch.sbb.matsim.analysis.skims;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GeometryData<T> {

    final Map<T, Integer> orig2index;
    final Map<T, Integer> dest2index;
    final ObjectMatrix<T> nodeGeometries;
    final ObjectMatrix<T> linksTravelled;

    GeometryData(Set<T> origins, Set<T> destinations) {
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

        this.nodeGeometries = new ObjectMatrix<>(origSize, destSize, orig2index, dest2index);
        this.linksTravelled = new ObjectMatrix<>(origSize, destSize, orig2index, dest2index);
    }
}
