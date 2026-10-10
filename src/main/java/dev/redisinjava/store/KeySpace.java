package dev.redisinjava.store;

import dev.redisinjava.util.Glob;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The keyspace: every key maps to a typed value plus optional expiry.
 *
 * <p>Expiry is lazy: {@link #get} and friends drop expired keys on access.
 * The event loop additionally calls {@link #sweepOnce} on a timer (active
 * expiry). Each key also carries a mutation version for {@code WATCH} and a
 * last-access timestamp for approximate LRU eviction.
 *
 * <p>Single-threaded by design; the event loop is the only caller, so no
 * locking is used.
 */
public final class KeySpace {
    private static final int ACTIVE_EXPIRE_SAMPLE = 20;

    private record Entry(RedisValue value, long expireAtMillis, long lastAccessNanos) {
        Entry withValue(RedisValue next) {
            return new Entry(next, expireAtMillis, System.nanoTime());
        }

        Entry withExpire(long atMillis) {
            return new Entry(value, atMillis, lastAccessNanos);
        }
    }

    private final Map<String, Entry> data = new HashMap<>();
    private final Map<String, Long> versions = new HashMap<>();
    private long usedMemoryBytes;
    private long sweepCursor;

    /** Returns the live value or null when missing/expired. Updates LRU clock. */
    public RedisValue get(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            removeInternal(key);
            return null;
        }
        data.put(key, new Entry(entry.value(), entry.expireAtMillis(), System.nanoTime()));
        return entry.value();
    }

    /** Returns the live value without touching the LRU clock. */
    public RedisValue peek(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            removeInternal(key);
            return null;
        }
        return entry.value();
    }

    /** Stores a value, preserving any existing TTL like real Redis does. */
    public void put(String key, RedisValue value) {
        Entry old = data.get(key);
        long expire = -1;
        if (old != null && !isExpired(old)) {
            expire = old.expireAtMillis();
            usedMemoryBytes -= old.value().estimateSize();
        } else if (old != null) {
            data.remove(key);
        } else {
            usedMemoryBytes += key.length() + 32;
        }
        usedMemoryBytes += value.estimateSize();
        data.put(key, new Entry(value, expire, System.nanoTime()));
        bumpVersion(key);
    }

    /** Stores a value, dropping any previous TTL (plain SET semantics). */
    /** Stores a value, dropping any previous TTL (plain SET semantics). */
    public void overwrite(String key, RedisValue value) {
        delete(key);
        data.put(key, new Entry(value, -1, System.nanoTime()));
        usedMemoryBytes += key.length() + 32 + value.estimateSize();
        bumpVersion(key);
    }

    /** Removes a key; true when something was actually deleted. */
    public boolean delete(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return false;
        }
        removeInternal(key);
        return true;
    }

    /** True when the key exists and is not expired. */
    public boolean contains(String key) {
        return peek(key) != null;
    }

    /** Absolute expiry in epoch millis, or -1 for persistent keys. */
    public long expireAt(String key) {
        Entry entry = data.get(key);
        if (entry == null || isExpired(entry)) {
            if (entry != null) {
                removeInternal(key);
            }
            return -2;
        }
        return entry.expireAtMillis();
    }

    /** Sets absolute expiry; false when the key does not exist. */
    public boolean setExpireAt(String key, long atMillis) {
        Entry entry = data.get(key);
        if (entry == null || isExpired(entry)) {
            if (entry != null) {
                removeInternal(key);
            }
            return false;
        }
        data.put(key, entry.withExpire(atMillis));
        bumpVersion(key);
        return true;
    }

    /** Clears the TTL; true when one was removed. */
    public boolean persist(String key) {
        Entry entry = data.get(key);
        if (entry == null || isExpired(entry)) {
            if (entry != null) {
                removeInternal(key);
            }
            return false;
        }
        if (entry.expireAtMillis() < 0) {
            return false;
        }
        data.put(key, entry.withExpire(-1));
        bumpVersion(key);
        return true;
    }

    public void clear() {
        for (String key : new ArrayList<>(data.keySet())) {
            removeInternal(key);
        }
    }

    /** Live key count (expired keys are purged first). */
    public long size() {
        sweepAll();
        return data.size();
    }

    /** Number of keys carrying a TTL. */
    public long expires() {
        sweepAll();
        long count = 0;
        for (Entry entry : data.values()) {
            if (entry.expireAtMillis() >= 0) {
                count++;
            }
        }
        return count;
    }

    /** Live keys matching a glob pattern. */
    public List<String> keys(String pattern) {
        List<String> out = new ArrayList<>();
        for (String key : new ArrayList<>(data.keySet())) {
            if (peek(key) != null && Glob.match(pattern, key)) {
                out.add(key);
            }
        }
        return out;
    }

    /** Mutation counter for optimistic locking ({@code WATCH}). */
    public long version(String key) {
        return versions.getOrDefault(key, 0L);
    }

    /**
     * One active-expiry step: sample a few keys, drop the expired ones.
     * Returns the number expired so the loop can repeat when many expired.
     */
    public int sweepOnce() {
        if (data.isEmpty()) {
            return 0;
        }
        List<String> keys = new ArrayList<>(data.keySet());
        int expired = 0;
        int checked = 0;
        int n = keys.size();
        for (int i = 0; i < ACTIVE_EXPIRE_SAMPLE && checked < n; i++) {
            sweepCursor = (sweepCursor + 1) % n;
            String key = keys.get((int) sweepCursor % keys.size());
            Entry entry = data.get(key);
            checked++;
            if (entry != null && isExpired(entry)) {
                removeInternal(key);
                expired++;
            }
        }
        return expired;
    }

    /** Up to {@code count} arbitrary live keys, for eviction sampling. */
    public List<String> sample(int count) {
        List<String> out = new ArrayList<>(count);
        int i = 0;
        for (String key : data.keySet()) {
            if (peek(key) != null) {
                out.add(key);
                if (++i >= count) {
                    break;
                }
            }
        }
        return out;
    }

    /** Last-access timestamp for LRU comparison; -1 for missing keys. */
    public long lastAccessNanos(String key) {
        Entry entry = data.get(key);
        return entry == null ? -1 : entry.lastAccessNanos();
    }

    public long usedMemoryBytes() {
        return Math.max(0, usedMemoryBytes);
    }

    private boolean isExpired(Entry entry) {
        return entry.expireAtMillis() >= 0
                && entry.expireAtMillis() <= System.currentTimeMillis();
    }

    private void removeInternal(String key) {
        Entry removed = data.remove(key);
        if (removed != null) {
            usedMemoryBytes -= removed.value().estimateSize() + key.length() + 32;
        }
        bumpVersion(key);
    }

    private void sweepAll() {
        for (String key : new ArrayList<>(data.keySet())) {
            peek(key);
        }
    }

    private void bumpVersion(String key) {
        versions.put(key, versions.getOrDefault(key, 0L) + 1);
    }
}
