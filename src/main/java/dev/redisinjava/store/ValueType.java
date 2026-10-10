package dev.redisinjava.store;

/** The five Redis value types plus {@code none} for missing keys. */
public enum ValueType {
    STRING("string"),
    LIST("list"),
    SET("set"),
    ZSET("zset"),
    HASH("hash"),
    NONE("none");

    private final String name;

    ValueType(String name) {
        this.name = name;
    }

    public String typeName() {
        return name;
    }
}
