package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.redisinjava.ServerHarness.RedisError;
import dev.redisinjava.ServerHarness.TestClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests for strings, keys and expiry over a live server socket. */
class StringAndKeysTest {
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
    void setAndGetRoundTrip() throws Exception {
        assertEquals("OK", client.cmd("SET", "k", "v"));
        assertEquals("v", client.cmd("GET", "k"));
        assertNull(client.cmd("GET", "missing"));
    }

    @Test
    void setNxAndXx() throws Exception {
        assertEquals("OK", client.cmd("SET", "k", "1"));
        assertNull(client.cmd("SET", "k", "2", "NX"));
        assertEquals("1", client.cmd("GET", "k"));
        assertEquals("OK", client.cmd("SET", "k", "2", "XX"));
        assertNull(client.cmd("SET", "absent", "x", "XX"));
        assertEquals("2", client.cmd("GET", "k"));
    }

    @Test
    void setWithExpiry() throws Exception {
        assertEquals("OK", client.cmd("SET", "k", "v", "EX", "100"));
        long ttl = (Long) client.cmd("TTL", "k");
        assertTrue(ttl >= 99 && ttl <= 100, "ttl=" + ttl);
        assertEquals("OK", client.cmd("SET", "j", "v", "PX", "5000"));
        long pttl = (Long) client.cmd("PTTL", "j");
        assertTrue(pttl > 4000 && pttl <= 5000, "pttl=" + pttl);
    }

    @Test
    void setClearsPreviousTtl() throws Exception {
        client.cmd("SET", "k", "v", "EX", "100");
        client.cmd("SET", "k", "v2");
        assertEquals(-1L, client.cmd("TTL", "k"));
    }

    @Test
    void setRejectsBadOptions() throws Exception {
        assertInstanceOf(RedisError.class, client.cmd("SET", "k", "v", "BOGUS"));
        assertInstanceOf(RedisError.class, client.cmd("SET", "k", "v", "NX", "XX"));
        assertInstanceOf(RedisError.class, client.cmd("SET", "k", "v", "EX", "abc"));
        assertInstanceOf(RedisError.class, client.cmd("SET", "k", "v", "EX", "0"));
    }

    @Test
    void incrDecrFamily() throws Exception {
        assertEquals(1L, client.cmd("INCR", "n"));
        assertEquals(6L, client.cmd("INCRBY", "n", "5"));
        assertEquals(5L, client.cmd("DECR", "n"));
        assertInstanceOf(RedisError.class, client.cmd("INCRBY", "n", "x"));
        client.cmd("SET", "big", "9223372036854775807");
        assertInstanceOf(RedisError.class, client.cmd("INCR", "big"));
        client.cmd("SET", "str", "abc");
        assertInstanceOf(RedisError.class, client.cmd("INCR", "str"));
    }

    @Test
    void incrPreservesTtl() throws Exception {
        client.cmd("SET", "n", "1", "EX", "100");
        client.cmd("INCR", "n");
        assertTrue((Long) client.cmd("TTL", "n") > 0);
    }

    @Test
    void appendAndStrlen() throws Exception {
        assertEquals(5L, client.cmd("APPEND", "k", "hello"));
        assertEquals(11L, client.cmd("APPEND", "k", " world"));
        assertEquals("hello world", client.cmd("GET", "k"));
        assertEquals(11L, client.cmd("STRLEN", "k"));
        assertEquals(0L, client.cmd("STRLEN", "missing"));
    }

    @Test
    void msetMget() throws Exception {
        assertEquals("OK", client.cmd("MSET", "a", "1", "b", "2"));
        assertEquals(java.util.Arrays.asList("1", "2", null), client.cmd("MGET", "a", "b", "c"));
        assertInstanceOf(RedisError.class, client.cmd("MSET", "a", "1", "b"));
    }

    @Test
    void delAndExists() throws Exception {
        client.cmd("MSET", "a", "1", "b", "2");
        assertEquals(2L, client.cmd("EXISTS", "a", "b", "c"));
        assertEquals(2L, client.cmd("DEL", "a", "b", "c"));
        assertEquals(0L, client.cmd("EXISTS", "a", "b"));
    }

    @Test
    void expireAndTtl() throws Exception {
        client.cmd("SET", "k", "v");
        assertEquals(1L, client.cmd("EXPIRE", "k", "100"));
        assertTrue((Long) client.cmd("TTL", "k") <= 100);
        assertEquals(1L, client.cmd("PEXPIRE", "k", "5000"));
        long pttl = (Long) client.cmd("PTTL", "k");
        assertTrue(pttl > 4000 && pttl <= 5000, "pttl=" + pttl);
        assertEquals(0L, client.cmd("EXPIRE", "missing", "10"));
        client.cmd("SET", "p", "v");
        assertEquals(-1L, client.cmd("TTL", "p"));
        assertEquals(-2L, client.cmd("TTL", "missing"));
    }

    @Test
    void pastExpiryDeletes() throws Exception {
        client.cmd("SET", "k", "v");
        assertEquals(1L, client.cmd("EXPIRE", "k", "-5"));
        assertNull(client.cmd("GET", "k"));
    }

    @Test
    void lazyExpiryOnRead() throws Exception {
        client.cmd("SET", "k", "v", "PX", "50");
        Thread.sleep(150);
        assertNull(client.cmd("GET", "k"));
        assertEquals(-2L, client.cmd("TTL", "k"));
    }

    @Test
    void activeExpirySweepsWithoutReads() throws Exception {
        client.cmd("SET", "k", "v", "PX", "50");
        Thread.sleep(400); // active sweep runs every 100ms
        assertEquals(0L, client.cmd("DBSIZE"));
    }

    @Test
    void persistTypeKeysFlushallDbsize() throws Exception {
        client.cmd("MSET", "a", "1", "b", "2", "user:1", "x");
        client.cmd("EXPIRE", "a", "100");
        assertEquals(1L, client.cmd("PERSIST", "a"));
        assertEquals(0L, client.cmd("PERSIST", "b"));
        assertEquals("string", client.cmd("TYPE", "a"));
        assertEquals("none", client.cmd("TYPE", "missing"));
        List<?> keys = (List<?>) client.cmd("KEYS", "user:*");
        assertEquals(java.util.Collections.singletonList("user:1"), keys);
        assertEquals(3L, client.cmd("DBSIZE"));
        assertEquals("OK", client.cmd("FLUSHALL"));
        assertEquals(0L, client.cmd("DBSIZE"));
    }

    @Test
    void wrongTypeAgainstFutureTypes() throws Exception {
        // GET on a string is fine; cross-type errors are covered per-type later.
        client.cmd("SET", "s", "v");
        assertEquals("string", client.cmd("TYPE", "s"));
        assertEquals("v", client.cmd("GET", "s"));
    }

    @Test
    void selectInfoCommand() throws Exception {
        assertEquals("OK", client.cmd("SELECT", "0"));
        assertInstanceOf(RedisError.class, client.cmd("SELECT", "1"));
        String info = (String) client.cmd("INFO");
        assertTrue(info.contains("role:master"));
        assertTrue(((Long) client.cmd("COMMAND", "COUNT")) > 20);
    }
}
