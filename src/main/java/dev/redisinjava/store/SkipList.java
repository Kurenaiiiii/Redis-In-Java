package dev.redisinjava.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A Redis-style skip list ordered by (score, member). Average O(log n)
 * insert, delete and rank; range scans walk level 0. Spans on every forward
 * pointer make rank queries logarithmic instead of linear.
 *
 * <p>Deliberately not a TreeMap: this is the data structure real Redis uses
 * for sorted sets, and the point of this project is to implement it.
 */
public final class SkipList {
    private static final int MAX_LEVEL = 32;
    private static final double PROBABILITY = 0.25;

    static final class Node {
        final double score;
        final String member;
        final Node[] forward;
        final int[] span;

        Node(int level, double score, String member) {
            this.score = score;
            this.member = member;
            this.forward = new Node[level];
            this.span = new int[level];
        }
    }

    /** Score/member pair returned by range scans. */
    public record Entry(double score, String member) {
    }

    private final Node header = new Node(MAX_LEVEL, Double.NaN, null);
    private final Random random;
    private int level = 1;
    private long length;

    public SkipList() {
        this(ThreadLocalRandom.current());
    }

    /** Seeded constructor for deterministic tests. */
    public SkipList(Random random) {
        this.random = random;
    }

    public long size() {
        return length;
    }

    /** -1, 0, 1 comparing (score, member) pairs. */
    private static int compare(double scoreA, String memberA, double scoreB, String memberB) {
        if (scoreA != scoreB) {
            return Double.compare(scoreA, scoreB);
        }
        return memberA.compareTo(memberB);
    }

    /** Inserts a new node; the caller guarantees the member is absent. */
    public void insert(double score, String member) {
        Node[] update = new Node[MAX_LEVEL];
        int[] rank = new int[MAX_LEVEL];
        Node cursor = header;
        for (int i = level - 1; i >= 0; i--) {
            rank[i] = i == level - 1 ? 0 : rank[i + 1];
            while (cursor.forward[i] != null
                    && compare(cursor.forward[i].score, cursor.forward[i].member, score, member) < 0) {
                rank[i] += cursor.span[i];
                cursor = cursor.forward[i];
            }
            update[i] = cursor;
        }
        int newLevel = randomLevel();
        if (newLevel > level) {
            for (int i = level; i < newLevel; i++) {
                rank[i] = 0;
                update[i] = header;
                update[i].span[i] = (int) length;
            }
            level = newLevel;
        }
        Node node = new Node(newLevel, score, member);
        for (int i = 0; i < newLevel; i++) {
            node.forward[i] = update[i].forward[i];
            update[i].forward[i] = node;
            node.span[i] = update[i].span[i] - (rank[0] - rank[i]);
            update[i].span[i] = rank[0] - rank[i] + 1;
        }
        for (int i = newLevel; i < level; i++) {
            update[i].span[i]++;
        }
        length++;
    }

    /** Deletes the node; true when it existed. */
    public boolean delete(double score, String member) {
        Node[] update = new Node[MAX_LEVEL];
        Node cursor = header;
        for (int i = level - 1; i >= 0; i--) {
            while (cursor.forward[i] != null
                    && compare(cursor.forward[i].score, cursor.forward[i].member, score, member) < 0) {
                cursor = cursor.forward[i];
            }
            update[i] = cursor;
        }
        Node target = cursor.forward[0];
        if (target == null || target.score != score || !target.member.equals(member)) {
            // NaN scores never equal; members are unique so identity check below is safer.
            if (target == null || !target.member.equals(member) || target.score != score) {
                return false;
            }
        }
        for (int i = 0; i < level; i++) {
            if (update[i].forward[i] == target) {
                update[i].span[i] += target.span[i] - 1;
                update[i].forward[i] = target.forward[i];
            } else {
                update[i].span[i]--;
            }
        }
        while (level > 1 && header.forward[level - 1] == null) {
            level--;
        }
        length--;
        return true;
    }

    /** 0-based rank of a member, or -1 when absent. */
    public long rank(double score, String member) {
        long rank = 0;
        Node cursor = header;
        for (int i = level - 1; i >= 0; i--) {
            while (cursor.forward[i] != null
                    && compare(cursor.forward[i].score, cursor.forward[i].member, score, member) <= 0) {
                rank += cursor.span[i];
                cursor = cursor.forward[i];
            }
            if (cursor.member != null && cursor.member.equals(member)) {
                return rank - 1;
            }
        }
        return -1;
    }

    /**
     * Members by 0-based rank in [start, stop] (inclusive, negatives allowed).
     */
    public List<Entry> rangeByRank(long start, long stop) {
        long size = length;
        if (start < 0) {
            start = size + start;
        }
        if (stop < 0) {
            stop = size + stop;
        }
        start = Math.max(start, 0);
        stop = Math.min(stop, size - 1);
        List<Entry> out = new ArrayList<>();
        if (start > stop || size == 0) {
            return out;
        }
        Node cursor = nodeAtRank(start);
        for (long i = start; i <= stop && cursor != null; i++) {
            out.add(new Entry(cursor.score, cursor.member));
            cursor = cursor.forward[0];
        }
        return out;
    }

    /**
     * Members with min &lt;= score &lt;= max (exclusive ends when flagged),
     * honouring offset/limit. Infinite bounds are expressed with
     * {@link Double#NEGATIVE_INFINITY} / {@link Double#POSITIVE_INFINITY}.
     */
    public List<Entry> rangeByScore(double min, boolean minExclusive, double max,
                                    boolean maxExclusive, long offset, long limit) {
        List<Entry> out = new ArrayList<>();
        Node cursor = firstInRange(min, minExclusive);
        long skipped = 0;
        while (cursor != null && withinMax(cursor.score, max, maxExclusive)) {
            if (skipped < offset) {
                skipped++;
            } else if (limit >= 0 && out.size() >= limit) {
                break;
            } else {
                out.add(new Entry(cursor.score, cursor.member));
            }
            cursor = cursor.forward[0];
        }
        return out;
    }

    private Node nodeAtRank(long target) {
        // Spans count nodes, so the search uses 1-based rank internally.
        long traversed = 0;
        Node cursor = header;
        for (int i = level - 1; i >= 0; i--) {
            while (cursor.forward[i] != null
                    && traversed + cursor.span[i] <= target + 1) {
                traversed += cursor.span[i];
                cursor = cursor.forward[i];
            }
        }
        return traversed == target + 1 ? cursor : null;
    }

    private Node firstInRange(double min, boolean minExclusive) {
        Node cursor = header;
        for (int i = level - 1; i >= 0; i--) {
            while (cursor.forward[i] != null && (cursor.forward[i].score < min
                    || (minExclusive && cursor.forward[i].score == min))) {
                cursor = cursor.forward[i];
            }
        }
        return cursor.forward[0];
    }

    private static boolean withinMax(double score, double max, boolean maxExclusive) {
        return score < max || (!maxExclusive && score == max);
    }

    private int randomLevel() {
        int next = 1;
        while (random.nextDouble() < PROBABILITY && next < MAX_LEVEL) {
            next++;
        }
        return next;
    }
}
