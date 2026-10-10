package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.redisinjava.ServerHarness.RedisError;
import dev.redisinjava.ServerHarness.TestClient;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests for MULTI/EXEC/DISCARD/WATCH over live sockets. */
class TransactionTest {
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
    void multiExecRunsAtomically() throws Exception {
        assertEquals("OK", client.cmd("MULTI"));
        assertEquals("QUEUED", client.cmd("SET", "a", "1"));
        assertEquals("QUEUED", client.cmd("INCR", "n"));
        assertEquals(Arrays.asList("OK", 1L), client.cmd("EXEC"));
        assertEquals("1", client.cmd("GET", "a"));
    }

    @Test
    void execWithoutMultiAndDiscardWithoutMulti() throws Exception {
        assertInstanceOf(RedisError.class, client.cmd("EXEC"));
        assertInstanceOf(RedisError.class, client.cmd("DISCARD"));
        assertInstanceOf(RedisError.class, client.cmd("MULTI", "extra"));
    }

    @Test
    void nestedMultiRejected() throws Exception {
        client.cmd("MULTI");
        assertInstanceOf(RedisError.class, client.cmd("MULTI"));
        assertEquals("OK", client.cmd("DISCARD"));
    }

    @Test
    void discardDropsQueue() throws Exception {
        client.cmd("MULTI");
        client.cmd("SET", "a", "1");
        assertEquals("OK", client.cmd("DISCARD"));
        assertNull(client.cmd("GET", "a"));
        assertInstanceOf(RedisError.class, client.cmd("EXEC"));
    }

    @Test
    void queueTimeErrorAbortsExec() throws Exception {
        client.cmd("MULTI");
        client.cmd("SET", "a", "1");
        assertInstanceOf(RedisError.class, client.cmd("NOSUCHCOMMAND"));
        Object exec = client.cmd("EXEC");
        assertInstanceOf(RedisError.class, exec);
        assertTrue(((RedisError) exec).message().startsWith("EXECABORT"));
        assertNull(client.cmd("GET", "a"));
    }

    @Test
    void runtimeErrorCollectedNotAborted() throws Exception {
        client.cmd("SET", "s", "notanint");
        client.cmd("MULTI");
        client.cmd("INCR", "s");
        client.cmd("SET", "a", "1");
        Object exec = client.cmd("EXEC");
        assertInstanceOf(java.util.List.class, exec);
        java.util.List<?> replies = (java.util.List<?>) exec;
        assertEquals(2, replies.size());
        assertInstanceOf(RedisError.class, replies.get(0));
        assertEquals("OK", replies.get(1));
    }

    @Test
    void watchTripsOnOtherClientWrite() throws Exception {
        TestClient other = harness.client();
        try {
            client.cmd("SET", "k", "1");
            assertEquals("OK", client.cmd("WATCH", "k"));
            other.cmd("SET", "k", "2");
            client.cmd("MULTI");
            client.cmd("SET", "k", "3");
            assertNull(client.cmd("EXEC"));
            assertEquals("2", client.cmd("GET", "k"));
        } finally {
            other.close();
        }
    }

    @Test
    void watchOwnWriteTripsToo() throws Exception {
        client.cmd("SET", "k", "1");
        client.cmd("WATCH", "k");
        client.cmd("SET", "k", "2");
        client.cmd("MULTI");
        assertNull(client.cmd("EXEC"));
    }

    @Test
    void unwatchClears() throws Exception {
        client.cmd("SET", "k", "1");
        client.cmd("WATCH", "k");
        client.cmd("SET", "k", "2");
        assertEquals("OK", client.cmd("UNWATCH"));
        client.cmd("MULTI");
        client.cmd("SET", "k", "3");
        assertEquals(Collections.singletonList("OK"), client.cmd("EXEC"));
    }

    @Test
    void watchMissingKeyTripsOnCreate() throws Exception {
        client.cmd("WATCH", "newkey");
        client.cmd("SET", "newkey", "v");
        client.cmd("MULTI");
        assertNull(client.cmd("EXEC"));
        // and a quiet key lets EXEC through
        client.cmd("WATCH", "quiet");
        client.cmd("MULTI");
        assertEquals(Collections.emptyList(), client.cmd("EXEC"));
    }
}
