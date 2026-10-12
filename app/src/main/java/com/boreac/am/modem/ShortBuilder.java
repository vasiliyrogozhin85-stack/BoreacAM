package com.boreac.am.modem;

import java.util.Arrays;

final class ShortBuilder {
    private short[] data;
    private int size;

    ShortBuilder(int initialCapacity) {
        data = new short[Math.max(64, initialCapacity)];
    }

    void add(short value) {
        ensure(size + 1);
        data[size++] = value;
    }

    void addAll(short[] values) {
        ensure(size + values.length);
        System.arraycopy(values, 0, data, size, values.length);
        size += values.length;
    }

    void addZeros(int count) {
        ensure(size + count);
        Arrays.fill(data, size, size + count, (short) 0);
        size += count;
    }

    short[] toArray() {
        return Arrays.copyOf(data, size);
    }

    private void ensure(int required) {
        if (required <= data.length) return;
        int next = data.length;
        while (next < required) next = Math.max(next + 1024, next * 2);
        data = Arrays.copyOf(data, next);
    }
}
