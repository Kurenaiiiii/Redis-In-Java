package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.redisinjava.ServerHarness.RedisError;
import dev.redisinjava.ServerHarness.TestClient;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests for sorted sets over a live server socket. */
class SortedSetTest {
    private ServerHarness harness;
    private TestClient client;

    @BeforeEach
    void start() throws Exception {
        harness = new ServerHarness();
        client = harness.client();
    }

    @AfterEach
    void stop() throws Exception {
        client.close();
        harness.close();
    }

    @Test
    void zaddZscoreZcardZrank() throws Exception {
        assertEquals(3L, client.cmd("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        assertEquals(0L, client.cmd("ZADD", "z", "2", "b"));
        assertEquals("2", client.cmd("ZSCORE", "z", "b"));
        assertNull(client.cmd("ZSCORE", "z", "nope"));
        assertNull(client.cmd("ZSCORE", "missing", "a"));
        assertEquals(3L, client.cmd("ZCARD", "z"));
        assertEquals(0L, client.cmd("ZCARD", "missing"));
        assertEquals(0L, client.cmd("ZRANK", "z", "a"));
        assertEquals(2L, client.cmd("ZRANK", "z", "c"));
        assertNull(client.cmd("ZRANK", "z", "nope"));
        assertEquals("zset", client.cmd("TYPE", "z"));
    }

    @Test
    void rescoreMovesMember() throws Exception {
        client.cmd("ZADD", "z", "1", "a", "2", "b", "3", "c");
        client.cmd("ZADD", "z", "10", "a");
        assertEquals(2L, client.cmd("ZRANK", "z", "a"));
        assertEquals("10", client.cmd("ZSCORE", "z", "a"));
    }

    @Test
    void equalScoresSortLexicographically() throws Exception {
        client.cmd("ZADD", "z", "1", "c", "1", "a", "1", "b");
        assertEquals(Arrays.asList("a", "b", "c"), client.cmd("ZRANGE", "z", "0", "-1"));
        assertEquals(0L, client.cmd("ZRANK", "z", "a"));
    }

    @Test
    void zrangeWithScoresAndSlices() throws Exception {
        client.cmd("ZADD", "z", "1", "a", "2.5", "b", "3", "c");
        assertEquals(Arrays.asList("a", "1", "b", "2.5", "c", "3"),
                client.cmd("ZRANGE", "z", "0", "-1", "WITHSCORES"));
        assertEquals(Collections.singletonList("c"), client.cmd("ZRANGE", "z", "-1", "-1"));
        assertEquals(Collections.emptyList(), client.cmd("ZRANGE", "z", "5", "10"));
        assertEquals(Collections.emptyList(), client.cmd("ZRANGE", "missing", "0", "-1"));
        assertInstanceOf(RedisError.class,
                client.cmd("ZRANGE", "z", "0", "-1", "BOGUS"));
    }

    @Test
    void zrangebyscore() throws Exception {
        client.cmd("ZADD", "z", "1", "a", "2", "b", "3", "c", "4", "d");
        assertEquals(Arrays.asList("b", "c"),
                client.cmd("ZRANGEBYSCORE", "z", "2", "3"));
        assertEquals(Arrays.asList("c"),
                client.cmd("ZRANGEBYSCORE", "z", "(2", "3"));
        assertEquals(Arrays.asList("b"),
                client.cmd("ZRANGEBYSCORE", "z", "2", "(3"));
        assertEquals(Arrays.asList("a", "b", "c", "d"),
                client.cmd("ZRANGEBYSCORE", "z", "-inf", "+inf"));
        assertEquals(Arrays.asList("b", "2", "c", "3"),
                client.cmd("ZRANGEBYSCORE", "z", "2", "3", "WITHSCORES"));
        assertEquals(Arrays.asList("b", "c"),
                client.cmd("ZRANGEBYSCORE", "z", "-inf", "+inf", "LIMIT", "1", "2"));
        assertInstanceOf(RedisError.class,
                client.cmd("ZRANGEBYSCORE", "z", "nan-pls", "3"));
    }

    @Test
    void zremAndEmptyRemoval() throws Exception {
        client.cmd("ZADD", "z", "1", "a", "2", "b");
        assertEquals(1L, client.cmd("ZREM", "z", "a", "nope"));
        assertEquals(0L, client.cmd("ZREM", "missing", "a"));
        assertEquals(1L, client.cmd("ZREM", "z", "b"));
        assertEquals("none", client.cmd("TYPE", "z"));
    }

    @Test
    void zaddRejectsBadFloats() throws Exception {
        assertInstanceOf(RedisError.class, client.cmd("ZADD", "z", "nan", "a"));
        assertInstanceOf(RedisError.class, client.cmd("ZADD", "z", "1", "a", "oops"));
        // nothing was stored by the failed ZADD
        assertEquals("none", client.cmd("TYPE", "z"));
    }

    @Test
    void wrongType() throws Exception {
        client.cmd("SET", "s", "v");
        assertInstanceOf(RedisError.class, client.cmd("ZADD", "s", "1", "a"));
        assertInstanceOf(RedisError.class, client.cmd("ZRANK", "s", "a"));
    }
}
