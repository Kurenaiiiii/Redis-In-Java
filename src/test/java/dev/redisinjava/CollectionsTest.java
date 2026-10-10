package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.redisinjava.ServerHarness.RedisError;
import dev.redisinjava.ServerHarness.TestClient;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests for lists, hashes and sets over a live server socket. */
class CollectionsTest {
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
    void listPushPop() throws Exception {
        assertEquals(3L, client.cmd("RPUSH", "k", "a", "b", "c"));
        assertEquals(5L, client.cmd("LPUSH", "k", "z", "y"));
        assertEquals("list", client.cmd("TYPE", "k"));
        assertEquals(5L, client.cmd("LLEN", "k"));
        assertEquals(Arrays.asList("y", "z", "a", "b", "c"),
                client.cmd("LRANGE", "k", "0", "-1"));
        assertEquals("y", client.cmd("LPOP", "k"));
        assertEquals("c", client.cmd("RPOP", "k"));
        assertEquals(Arrays.asList("z", "a"), client.cmd("LPOP", "k", "2"));
        assertEquals("b", client.cmd("LPOP", "k"));
        assertEquals(0L, client.cmd("LLEN", "k"));
        assertEquals("none", client.cmd("TYPE", "k")); // emptied lists vanish
    }

    @Test
    void listEdges() throws Exception {
        assertNull(client.cmd("LPOP", "missing"));
        assertEquals(Collections.emptyList(), client.cmd("LPOP", "missing", "3"));
        assertEquals(Collections.emptyList(), client.cmd("LRANGE", "missing", "0", "-1"));
        client.cmd("RPUSH", "k", "a", "b");
        assertEquals(Collections.emptyList(), client.cmd("LRANGE", "k", "5", "10"));
        assertEquals(Arrays.asList("a", "b"), client.cmd("LRANGE", "k", "-100", "100"));
        assertEquals(Arrays.asList("b"), client.cmd("LRANGE", "k", "-1", "-1"));
        assertInstanceOf(RedisError.class, client.cmd("LPOP", "k", "x"));
        assertInstanceOf(RedisError.class, client.cmd("LPOP", "k", "-1"));
    }

    @Test
    void hashCrud() throws Exception {
        assertEquals(2L, client.cmd("HSET", "h", "f1", "v1", "f2", "v2"));
        assertEquals(0L, client.cmd("HSET", "h", "f1", "v1b"));
        assertEquals("v1b", client.cmd("HGET", "h", "f1"));
        assertNull(client.cmd("HGET", "h", "nope"));
        assertNull(client.cmd("HGET", "missing", "f"));
        assertEquals(1L, client.cmd("HEXISTS", "h", "f1"));
        assertEquals(0L, client.cmd("HEXISTS", "h", "nope"));
        assertEquals(Arrays.asList("f1", "v1b", "f2", "v2"), client.cmd("HGETALL", "h"));
        assertEquals(1L, client.cmd("HDEL", "h", "f1", "nope"));
        assertEquals(1L, client.cmd("HDEL", "h", "f2"));
        assertEquals("none", client.cmd("TYPE", "h")); // emptied hashes vanish
    }

    @Test
    void setCrud() throws Exception {
        assertEquals(2L, client.cmd("SADD", "s", "a", "b", "a"));
        assertEquals(1L, client.cmd("SISMEMBER", "s", "a"));
        assertEquals(0L, client.cmd("SISMEMBER", "s", "z"));
        assertEquals(2L, client.cmd("SCARD", "s"));
        assertEquals(new HashSet<>(Arrays.asList("a", "b")),
                new HashSet<>((List<?>) client.cmd("SMEMBERS", "s")));
        assertEquals(1L, client.cmd("SREM", "s", "a", "nope"));
        assertEquals(0L, client.cmd("SCARD", "missing"));
        assertEquals(1L, client.cmd("SREM", "s", "b"));
        assertEquals("none", client.cmd("TYPE", "s")); // emptied sets vanish
    }

    @Test
    void crossTypeErrors() throws Exception {
        client.cmd("SET", "str", "v");
        client.cmd("RPUSH", "list", "x");
        client.cmd("HSET", "hash", "f", "v");
        client.cmd("SADD", "set", "m");
        assertInstanceOf(RedisError.class, client.cmd("GET", "list"));
        assertInstanceOf(RedisError.class, client.cmd("LPOP", "str"));
        assertInstanceOf(RedisError.class, client.cmd("HGET", "set", "f"));
        assertInstanceOf(RedisError.class, client.cmd("SADD", "hash", "m"));
        assertInstanceOf(RedisError.class, client.cmd("LLEN", "str"));
        assertInstanceOf(RedisError.class, client.cmd("MGET", "str", "list"));
    }

    @Test
    void collectionsExpire() throws Exception {
        client.cmd("RPUSH", "k", "a");
        client.cmd("EXPIRE", "k", "100");
        assertTrue((Long) client.cmd("TTL", "k") > 0);
        assertEquals("list", client.cmd("TYPE", "k"));
    }
}
