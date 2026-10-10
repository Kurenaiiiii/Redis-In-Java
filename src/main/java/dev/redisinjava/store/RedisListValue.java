package dev.redisinjava.store;

import java.util.ArrayDeque;
import java.util.Deque;

/** A list value; head is the left end (index 0). */
public final class RedisListValue extends RedisValue {
    private final Deque<byte[]> items = new ArrayDeque<>();

    public Deque<byte[]> items() {
        return items;
    }

    @Override
    public ValueType type() {
        return ValueType.LIST;
    }

    @Override
    public long estimateSize() {
        long size = 64;
        for (byte[] item : items) {
            size += 16 + item.length;
        }
        return size;
    }
}
