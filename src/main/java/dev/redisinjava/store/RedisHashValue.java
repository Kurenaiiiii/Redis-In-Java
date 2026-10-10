package dev.redisinjava.store;

import java.util.LinkedHashMap;
import java.util.Map;

/** A hash value: insertion-ordered field map (ordering is our own choice). */
public final class RedisHashValue extends RedisValue {
    private final Map<String, byte[]> fields = new LinkedHashMap<>();

    public Map<String, byte[]> fields() {
        return fields;
    }

    @Override
    public ValueType type() {
        return ValueType.HASH;
    }

    @Override
    public long estimateSize() {
        long size = 64;
        for (Map.Entry<String, byte[]> entry : fields.entrySet()) {
            size += 32 + entry.getKey().length() + entry.getValue().length;
        }
        return size;
    }
}
