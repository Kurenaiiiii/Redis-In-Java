package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.redisinjava.ServerHarness.RedisError;
import dev.redisinjava.ServerHarness.TestClient;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CONFIG, eviction, connection limits and idle timeout over live sockets. */
class ConfigLimitsTest {
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
    void configGetSet() throws Exception {
        assertEquals(Arrays.asList("maxmemory", "0"), client.cmd("CONFIG", "GET", "maxmemory"));
        List<?> wildcard = (List<?>) client.cmd("CONFIG", "GET", "max*");
        assertTrue(wildcard.contains("maxmemory") && wildcard.contains("maxclients"));
        assertEquals("OK", client.cmd("CONFIG", "SET", "maxmemory", "100mb"));
        assertEquals(Arrays.asList("maxmemory", "104857600"),
                client.cmd("CONFIG", "GET", "maxmemory"));
        assertEquals("OK", client.cmd("CONFIG", "SET", "maxmemory-policy", "allkeys-lru"));
        assertInstanceOf(RedisError.class,
                client.cmd("CONFIG", "SET", "maxmemory-policy", "nonsense"));
        assertInstanceOf(RedisError.class,
                client.cmd("CONFIG", "SET", "no-such-setting", "1"));
        assertInstanceOf(RedisError.class, client.cmd("CONFIG", "NOSUCHOP"));
        assertEquals("OK", client.cmd("CONFIG", "SET", "loglevel", "debug"));
        assertEquals(Arrays.asList("loglevel", "debug"), client.cmd("CONFIG", "GET", "loglevel"));
        assertEquals("OK", client.cmd("CONFIG", "SET", "loglevel", "info"));
    }

    @Test
    void allkeysLruEvictsUnderPressure() throws Exception {
        client.cmd("CONFIG", "SET", "maxmemory", "5000");
        client.cmd("CONFIG", "SET", "maxmemory-policy", "allkeys-lru");
        String big = "x".repeat(1500);
        for (int i = 0; i < 8; i++) {
            assertEquals("OK", client.cmd("SET", "k" + i, big));
            // touch k0 often so it stays the most-recently-used key
            client.cmd("GET", "k0");
        }
        long size = (Long) client.cmd("DBSIZE");
        assertTrue(size < 8, "expected evictions, dbsize=" + size);
        assertEquals(big, client.cmd("GET", "k0"));
    }

    @Test
    void noevictionFailsWritesWithOom() throws Exception {
        client.cmd("CONFIG", "SET", "maxmemory", "1500");
        client.cmd("CONFIG", "SET", "maxmemory-policy", "noeviction");
        String big = "x".repeat(1500);
        assertEquals("OK", client.cmd("SET", "a", big));
        Object reply = client.cmd("SET", "b", big);
        assertInstanceOf(RedisError.class, reply);
        assertTrue(((RedisError) reply).message().startsWith("OOM"));
        // reads still work under memory pressure
        assertEquals(big, client.cmd("GET", "a"));
    }

    @Test
    void maxclientsRefusesExtraConnections() throws Exception {
        assertEquals("OK", client.cmd("CONFIG", "SET", "maxclients", "1"));
        TestClient second = harness.client();
        try {
            // the second connection is closed immediately by the server
            boolean closed = false;
            try {
                second.cmd("PING");
            } catch (Exception e) {
                closed = true;
            }
            assertTrue(closed, "expected the extra connection to be refused");
        } finally {
            second.close();
        }
        assertEquals("OK", client.cmd("CONFIG", "SET", "maxclients", "10000"));
    }

    @Test
    void idleTimeoutClosesQuietConnections() throws Exception {
        assertEquals("OK", client.cmd("CONFIG", "SET", "timeout", "1"));
        Thread.sleep(2500);
        boolean closed = false;
        try {
            client.cmd("PING");
        } catch (Exception e) {
            closed = true;
        }
        assertTrue(closed, "expected idle connection to be closed");
    }

    @Test
    void protocolErrors() throws Exception {
        assertInstanceOf(RedisError.class, client.cmd("NOSUCHCOMMAND"));
        assertInstanceOf(RedisError.class, client.cmd("GET"));
        assertInstanceOf(RedisError.class, client.cmd("SET", "onlykey"));
        // server still serves after errors
        assertEquals("PONG", client.cmd("PING"));
    }
}
