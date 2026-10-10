package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.redisinjava.ServerHarness.TestClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** AOF persistence: replay across restarts, rewrite compaction, torn tail. */
class PersistenceTest {
    private Path tempAof() throws Exception {
        Path dir = Files.createTempDirectory("aof-test");
        dir.toFile().deleteOnExit();
        return dir.resolve("appendonly.aof");
    }

    @Test
    void replayRestoresEveryType() throws Exception {
        Path aof = tempAof();
        try (ServerHarness first = new ServerHarness(new dev.redisinjava.server.ServerConfig(), aof, true);
                TestClient client = first.client()) {
            client.cmd("SET", "str", "hello");
            client.cmd("RPUSH", "list", "a", "b");
            client.cmd("HSET", "hash", "f", "v");
            client.cmd("SADD", "set", "m1", "m2");
            client.cmd("ZADD", "z", "1.5", "mem");
            client.cmd("EXPIRE", "str", "100000");
        }
        try (ServerHarness second = new ServerHarness(new dev.redisinjava.server.ServerConfig(), aof, true);
                TestClient client = second.client()) {
            assertEquals("hello", client.cmd("GET", "str"));
            assertEquals(Arrays.asList("a", "b"), client.cmd("LRANGE", "list", "0", "-1"));
            assertEquals("v", client.cmd("HGET", "hash", "f"));
            assertEquals(1L, client.cmd("SISMEMBER", "set", "m1"));
            assertEquals("1.5", client.cmd("ZSCORE", "z", "mem"));
            assertTrue((Long) client.cmd("TTL", "str") > 90000);
            assertEquals(5L, client.cmd("DBSIZE"));
        }
    }

    @Test
    void rewriteCompactsAndStaysCorrect() throws Exception {
        Path aof = tempAof();
        try (ServerHarness harness = new ServerHarness(new dev.redisinjava.server.ServerConfig(), aof, true);
                TestClient client = harness.client()) {
            for (int i = 0; i < 200; i++) {
                client.cmd("SET", "k" + i, "v" + i);
                client.cmd("INCR", "counter");
            }
            for (int i = 0; i < 100; i++) {
                client.cmd("DEL", "k" + i);
            }
            long before = Files.size(aof);
            Object reply = client.cmd("BGREWRITEAOF");
            assertTrue(reply.toString().startsWith("Background append only"),
                    "reply=" + reply);
            long after = Files.size(aof);
            assertTrue(after < before, "before=" + before + " after=" + after);
            assertEquals("v150", client.cmd("GET", "k150"));
            assertNull(client.cmd("GET", "k10"));
            assertEquals("200", client.cmd("GET", "counter"));
        }
        // rewritten file replays identically
        try (ServerHarness second = new ServerHarness(new dev.redisinjava.server.ServerConfig(), aof, true);
                TestClient client = second.client()) {
            assertEquals("v150", client.cmd("GET", "k150"));
            assertEquals("200", client.cmd("GET", "counter"));
            assertEquals(101L, client.cmd("DBSIZE"));
        }
    }

    @Test
    void tornTailStillReplays() throws Exception {
        Path aof = tempAof();
        try (ServerHarness first = new ServerHarness(new dev.redisinjava.server.ServerConfig(), aof, true);
                TestClient client = first.client()) {
            client.cmd("SET", "good", "1");
        }
        Files.write(aof, "*3\r\n$3\r\nSET\r\n$4\r\ntorn".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        try (ServerHarness second = new ServerHarness(new dev.redisinjava.server.ServerConfig(), aof, true);
                TestClient client = second.client()) {
            assertEquals("1", client.cmd("GET", "good"));
        }
    }
}
