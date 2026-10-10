package dev.redisinjava.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Skiplist tests against a TreeMap reference: randomized inserts, deletes,
 * ranks and ranges over thousands of operations with fixed seeds.
 */
class SkipListTest {
    private static TreeMap<ScoredMember, Integer> reference() {
        return new TreeMap<>(Comparator
                .comparingDouble(ScoredMember::score)
                .thenComparing(ScoredMember::member));
    }

    private record ScoredMember(double score, String member) {
    }

    @Test
    void orderedInsertAndRank() {
        SkipList list = new SkipList(new Random(1));
        list.insert(3, "c");
        list.insert(1, "a");
        list.insert(2, "b");
        assertEquals(3, list.size());
        assertEquals(0, list.rank(1, "a"));
        assertEquals(1, list.rank(2, "b"));
        assertEquals(2, list.rank(3, "c"));
        assertEquals(-1, list.rank(9, "missing"));
    }

    @Test
    void equalScoresOrderLexicographically() {
        SkipList list = new SkipList(new Random(2));
        list.insert(1, "c");
        list.insert(1, "a");
        list.insert(1, "b");
        List<SkipList.Entry> range = list.rangeByRank(0, -1);
        assertEquals(List.of("a", "b", "c"),
                range.stream().map(SkipList.Entry::member).toList());
    }

    @Test
    void deleteFixesRanks() {
        SkipList list = new SkipList(new Random(3));
        for (int i = 0; i < 10; i++) {
            list.insert(i, "m" + i);
        }
        assertTrue(list.delete(4, "m4"));
        assertEquals(9, list.size());
        assertEquals(4, list.rank(5, "m5"));
        assertEquals(List.of(), list.rangeByRank(9, 20));
    }

    @Test
    void scoreRangesWithExclusiveBounds() {
        SkipList list = new SkipList(new Random(4));
        list.insert(1, "a");
        list.insert(2, "b");
        list.insert(3, "c");
        assertEquals(List.of("b", "c"), members(
                list.rangeByScore(1, true, 3, false, 0, -1)));
        assertEquals(List.of("a"), members(
                list.rangeByScore(Double.NEGATIVE_INFINITY, false, 2, true, 0, -1)));
        assertEquals(List.of("c"), members(
                list.rangeByScore(1, false, Double.POSITIVE_INFINITY, false, 2, -1)));
        assertEquals(List.of("a", "b"), members(
                list.rangeByScore(1, false, 3, false, 0, 2)));
    }

    @Test
    void randomizedAgainstTreeMap() {
        for (long seed : new long[] {7, 42, 1234}) {
            runRandomized(seed);
        }
    }

    private void runRandomized(long seed) {
        Random random = new Random(seed);
        SkipList list = new SkipList(new Random(seed));
        TreeMap<ScoredMember, Integer> ref = reference();
        Map<String, Double> live = new java.util.HashMap<>();

        for (int step = 0; step < 3000; step++) {
            int op = random.nextInt(100);
            if (op < 60 || live.isEmpty()) {
                String member = "m" + random.nextInt(400);
                double score = random.nextInt(50) + random.nextDouble();
                Double old = live.put(member, score);
                if (old != null) {
                    ref.remove(new ScoredMember(old, member));
                    list.delete(old, member);
                }
                ref.put(new ScoredMember(score, member), 1);
                list.insert(score, member);
            } else if (op < 80) {
                String member = "m" + random.nextInt(400);
                Double old = live.remove(member);
                if (old != null) {
                    ref.remove(new ScoredMember(old, member));
                    assertTrue(list.delete(old, member), "seed " + seed);
                }
            } else {
                // verify full ordering + every rank
                List<String> expected = new ArrayList<>(ref.keySet()).stream()
                        .map(ScoredMember::member).toList();
                assertEquals(expected.size(), list.size(), "seed " + seed);
                List<String> actual = members(list.rangeByRank(0, -1));
                assertEquals(expected, actual, "seed " + seed + " step " + step);
                int rank = 0;
                for (ScoredMember key : ref.keySet()) {
                    assertEquals(rank, list.rank(key.score(), key.member()),
                            "seed " + seed + " member " + key.member());
                    rank++;
                }
            }
        }
    }

    private static List<String> members(List<SkipList.Entry> entries) {
        return entries.stream().map(SkipList.Entry::member).toList();
    }
}
