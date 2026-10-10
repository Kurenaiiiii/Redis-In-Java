package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.redisinjava.ServerHarness.TestClient;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Connection handling: echo, quit, binary safety and pipelining. */
class ConnectionTest {
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
    void pingVariantsAndEcho() throws Exception {
        assertEquals("PONG", client.cmd("PING"));
        assertEquals("hello", client.cmd("PING", "hello"));
        assertEquals("hello", client.cmd("ECHO", "hello"));
    }

    @Test
    void quitClosesAfterReply() throws Exception {
        assertEquals("OK", client.cmd("QUIT"));
        assertThrows(IOException.class, client::read);
    }

    @Test
    void binarySafeRoundTrip() throws Exception {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            payload.append((char) i);
        }
        String blob = payload.toString();
        assertEquals("OK", client.cmd("SET", "bin", blob));
        assertEquals(blob, client.cmd("GET", "bin"));
        assertEquals(256L, client.cmd("STRLEN", "bin"));
    }

    @Test
    void pipelinedCommandsAllReply() throws Exception {
        client.sendMany(
                new String[] {"SET", "a", "1"},
                new String[] {"SET", "b", "2"},
                new String[] {"MGET", "a", "b", "c"},
                new String[] {"PING"});
        assertEquals("OK", client.read());
        assertEquals("OK", client.read());
        assertEquals(Arrays.asList("1", "2", null), client.read());
        assertEquals("PONG", client.read());
    }

    @Test
    void splitPacketCommandStillParses() throws Exception {
        // fragment a command across two TCP writes (Nagle off, still two packets)
        try (java.net.Socket raw = new java.net.Socket("127.0.0.1", harness.port())) {
            raw.setTcpNoDelay(true);
            raw.setSoTimeout(10000);
            var out = raw.getOutputStream();
            var in = raw.getInputStream();
            out.write("*3\r\n$3\r\nSET\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            Thread.sleep(150); // let the partial command sit in the server buffer
            out.write("$5\r\nkey55\r\n$1\r\nv\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            byte[] reply = in.readNBytes(5);
            assertEquals("+OK\r\n", new String(reply, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
