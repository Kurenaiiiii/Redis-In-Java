package dev.redisinjava.store;

import java.util.HashMap;
import java.util.Map;

/**
 * A sorted-set value: member&rarr;score map for O(1) lookups plus the skip
 * list for ordering and ranks. Both are updated together.
 */
public final class RedisZSetValue extends RedisValue {
    private final Map<String, Double> scores = new HashMap<>();
    private final SkipList skiplist = new SkipList();

    public Map<String, Double> scores() {
        return scores;
    }

    public SkipList skiplist() {
        return skiplist;
    }

    /** Adds or re-scores a member; true when the member is new. */
    public boolean add(String member, double score) {
        Double old = scores.get(member);
        if (old != null && Double.compare(old, score) == 0) {
            return false;
        }
        if (old != null) {
            skiplist.delete(old, member);
        }
        scores.put(member, score);
        skiplist.insert(score, member);
        return old == null;
    }

    /** Removes a member; true when it existed. */
    public boolean remove(String member) {
        Double old = scores.remove(member);
        if (old == null) {
            return false;
        }
        skiplist.delete(old, member);
        return true;
    }

    @Override
    public ValueType type() {
        return ValueType.ZSET;
    }

    @Override
    public long estimateSize() {
        long size = 64;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            size += 64 + entry.getKey().length();
        }
        return size;
    }
}
