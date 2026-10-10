package dev.redisinjava.store;

import java.util.LinkedHashSet;
import java.util.Set;

/** A set value: unique members, insertion-ordered for deterministic output. */
public final class RedisSetValue extends RedisValue {
    private final Set<String> members = new LinkedHashSet<>();

    public Set<String> members() {
        return members;
    }

    @Override
    public ValueType type() {
        return ValueType.SET;
    }

    @Override
    public long estimateSize() {
        long size = 64;
        for (String member : members) {
            size += 32 + member.length();
        }
        return size;
    }
}
