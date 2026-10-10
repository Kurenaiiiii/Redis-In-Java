package dev.redisinjava.persistence;

/** How often the AOF buffer is fsynced to disk. */
public enum FlushPolicy {
    ALWAYS,
    EVERYSEC,
    NO
}
