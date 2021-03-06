/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrix;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A simple m x m matrix storing float values, using arbitrary objects to identify rows and columns.
 * The list of identifying objects must be known before-hand when instantiating a matrix.
 *
 * @param &lt;T&gt; identifier type for matrix entries
 *
 * @author mrieser / SBB
 *
 * Design considerations:
 * The matrix stores all cell values in one big array in order to have it as compact as possible.
 * Row/Column identifiers are indexed once at initialization in order to compute the indices in the
 * array. Thus, each entry/cell uses 4 bytes. A 1000x1000 matrix would thus consume 4 MB of RAM.
 *
 * Alternative implementations like Map&lt;T, Map&lt;T, Float&gt;&gt; or Map&lt;Tuple&lt;T, T&gt;, Float&gt; would be far
 * less efficient, even for sparse arrays.
 * Map&lt;T, Map&lt;T, Float&gt;&gt; requires 48 bytes for each Map.Entry (16 bytes object header, 2 * 8 bytes
 * for Key and Value object reference, next pointer (8 bytes), int hash). Each stored Float consumes
 * 24 Bytes, so a 1000x1000 matrix would consume 1000*48 + 1000*1000*(48+24) = ca 70 MB (factor 16.5).
 * Map&lt;Tuple&lt;T, T&gt;, Float&gt; requires 48 bytes for each Map.Entry, each Tuple would use 32 bytes (16 object
 * header + 2 * 8 object pointers), Float object 24 bytes, so in total 1000x1000 would consume
 * 1000*1000*(48+32+24) = ca 100 MB (factor 25).
 *
 * So, as long as the matrix has entries in at least 1/16.5 = 6% or 1/25 = 4% of all cells, the
 * simple float array should be more efficient.
 *
 * For larger matrices the absolute volumes become even more impressive. For an 8000x8000 matrix,
 * the float array will use 250MB, while the alternatives will use 4.5 or 6.5 GB respectively.
 */
public class FloatMatrix<T> {

    public final Map<T, Integer> orig2index;
    public final Map<T, Integer> dest2index;
    private final int origSize;
    private final int destSize;
    private final float[] data;

    public FloatMatrix(Set<T> zones, float defaultValue) {
        this.origSize = zones.size();
        this.destSize = zones.size();
        this.orig2index = new HashMap<>((int) (this.origSize * 1.5));
        this.dest2index = new HashMap<>((int) (this.destSize * 1.5));
        this.data = new float[this.origSize * this.destSize];
        Arrays.fill(this.data, defaultValue);
        int index = 0;
        for(T z : zones) {
            this.orig2index.put(z, index);
            this.dest2index.put(z, index);
            index++;
        }
    }

    public FloatMatrix(int origSize, int destSize, Map<T, Integer> orig2index, Map<T, Integer> dest2index, float defaultValue) {
        this.origSize = origSize;
        this.destSize = destSize;
        this.orig2index = orig2index;
        this.dest2index = dest2index;
        this.data = new float[this.origSize * this.destSize];
        Arrays.fill(this.data, defaultValue);
    }

    public float set(T from, T to, float value) {
        int index = getIndex(from, to);
        float oldValue = this.data[index];
        this.data[index] = value;
        return oldValue;
    }

    public float get(T from, T to) {
        int index = getIndex(from, to);
        return this.data[index];
    }

    public float add(T from, T to, float value) {
        int index = getIndex(from, to);
        float oldValue = this.data[index];
        float newValue = oldValue + value;
        this.data[index] = newValue;
        return newValue;
    }

    /**
     * @param from
     * @param to
     * @param factor
     * @return the new value
     */
    public float multiply(T from, T to, float factor) {
        int index = getIndex(from, to);
        float oldValue = this.data[index];
        float newValue = oldValue * factor;
        this.data[index] = newValue;
        return newValue;
    }

    /**
     * Multiplies the values in every cell with the given factor.
     *
     * @param factor the multiplication factor
     */
    public void multiply(float factor) {
        for (int i = 0; i < this.data.length; i++) {
            this.data[i] *= factor;
        }
    }

    private int getIndex(T from, T to) {
        int fromIndex = this.orig2index.get(from);
        int toIndex = this.dest2index.get(to);
        return fromIndex * this.destSize + toIndex;
    }
}
