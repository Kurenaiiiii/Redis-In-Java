package dev.redisinjava.store;

/**
 * A value in the keyspace. Subclasses hold the native structure for one
 * {@link ValueType}; the keyspace treats them opaquely except for
 * {@link #estimateSize}, which feeds maxmemory accounting.
 */
public abstract class RedisValue {
    public abstract ValueType type();

    /** Approximate memory footprint in bytes for eviction accounting. */
    public abstract long estimateSize();
}
