package dev.redisinjava.store;

/** A plain string value; payload is raw bytes (binary-safe). */
public final class RedisStringValue extends RedisValue {
    private final byte[] data;

    public RedisStringValue(byte[] data) {
        this.data = data;
    }

    public byte[] data() {
        return data;
    }

    @Override
    public ValueType type() {
        return ValueType.STRING;
    }

    @Override
    public long estimateSize() {
        return 32L + data.length;
    }
}
