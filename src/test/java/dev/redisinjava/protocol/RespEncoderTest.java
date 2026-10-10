package dev.redisinjava.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Unit tests for RESP2 encoding of every reply type. */
class RespEncoderTest {
    private static String encode(RespValue value) {
        return new String(RespEncoder.encode(value), StandardCharsets.UTF_8);
    }

    @Test
    void encodesSimpleString() {
        assertEquals("+OK\r\n", encode(RespValue.ok()));
    }

    @Test
    void encodesError() {
        assertEquals("-ERR boom\r\n", encode(RespValue.err("ERR boom")));
    }

    @Test
    void encodesInteger() {
        assertEquals(":-7\r\n", encode(RespValue.intVal(-7)));
    }

    @Test
    void encodesBulkString() {
        assertEquals("$5\r\nhello\r\n",
                encode(RespValue.bulk("hello".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void encodesNilBulk() {
        assertEquals("$-1\r\n", encode(RespValue.nilBulk()));
    }

    @Test
    void encodesNilArray() {
        assertEquals("*-1\r\n", encode(RespValue.nilArray()));
    }

    @Test
    void encodesNestedArray() {
        RespValue value = RespValue.array(
                RespValue.bulk("a".getBytes(StandardCharsets.UTF_8)),
                RespValue.intVal(1),
                RespValue.nilBulk());
        assertEquals("*3\r\n$1\r\na\r\n:1\r\n$-1\r\n", encode(value));
    }

    @Test
    void binaryPayloadRoundTrips() {
        byte[] payload = new byte[] {0x00, (byte) 0xC3, 0x28};
        byte[] raw = RespEncoder.encode(RespValue.array(RespValue.bulk(payload)));
        RespParser parser = new RespParser();
        parser.feed(raw, 0, raw.length);
        RespValue.Array parsed = (RespValue.Array) parser.drain().get(0);
        assertArrayEquals(payload, ((RespValue.BulkString) parsed.items().get(0)).value());
    }
}
