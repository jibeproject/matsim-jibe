/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.skims;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Same idea as float matrix but for shorts
 */
public class ShortMatrix<T> {

    final Map<T, Integer> orig2index;
    final Map<T, Integer> dest2index;
    private final int origSize;
    private final int destSize;
    private final short[] data;

    public ShortMatrix(Set<T> zones, short defaultValue) {
        this.origSize = zones.size();
        this.destSize = zones.size();
        this.orig2index = new HashMap<>((int) (this.origSize * 1.5));
        this.dest2index = new HashMap<>((int) (this.destSize * 1.5));
        this.data = new short[this.origSize * this.destSize];
        Arrays.fill(this.data, defaultValue);
        int index = 0;
        for(T z : zones) {
            this.orig2index.put(z, index);
            this.dest2index.put(z, index);
            index++;
        }
    }

    public ShortMatrix(int origSize, int destSize, Map<T, Integer> orig2index, Map<T, Integer> dest2index, short defaultValue) {
        this.origSize = origSize;
        this.destSize = destSize;
        this.orig2index = orig2index;
        this.dest2index = dest2index;
        this.data = new short[this.origSize * this.destSize];
        Arrays.fill(this.data, defaultValue);
    }

    public short set(T from, T to, short value) {
        int index = getIndex(from, to);
        short oldValue = this.data[index];
        this.data[index] = value;
        return oldValue;
    }

    public short get(T from, T to) {
        int index = getIndex(from, to);
        return this.data[index];
    }

    public short add(T from, T to, short value) {
        int index = getIndex(from, to);
        short oldValue = this.data[index];
        short newValue = (short) (oldValue + value);
        this.data[index] = newValue;
        return newValue;
    }

    private int getIndex(T from, T to) {
        int fromIndex = this.orig2index.get(from);
        int toIndex = this.dest2index.get(to);
        return fromIndex * this.destSize + toIndex;
    }
}
