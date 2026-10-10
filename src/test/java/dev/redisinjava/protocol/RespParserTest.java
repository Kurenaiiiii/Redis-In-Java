package dev.redisinjava.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for incremental parsing: partial reads and pipelining. */
class RespParserTest {
    private static RespParser fed(byte[]... chunks) {
        RespParser parser = new RespParser();
        for (byte[] chunk : chunks) {
            parser.feed(chunk, 0, chunk.length);
        }
        return parser;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesSimpleCommand() {
        List<RespValue> values = fed(bytes("*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n")).drain();
        assertEquals(1, values.size());
        RespValue.Array array = assertInstanceOf(RespValue.Array.class, values.get(0));
        assertEquals(2, array.items().size());
    }

    @Test
    void parsesCommandSplitAcrossPackets() {
        RespParser parser = new RespParser();
        parser.feed(bytes("*2\r\n$4\r\nEC"), 0, "*2\r\n$4\r\nEC".length());
        assertTrue(parser.drain().isEmpty());
        parser.feed(bytes("HO\r\n$5\r\nhel"), 0, "HO\r\n$5\r\nhel".length());
        assertTrue(parser.drain().isEmpty());
        byte[] last = bytes("lo\r\n");
        parser.feed(last, 0, last.length);
        List<RespValue> values = parser.drain();
        assertEquals(1, values.size());
        RespValue.Array array = assertInstanceOf(RespValue.Array.class, values.get(0));
        assertEquals("ECHO", new String(
                ((RespValue.BulkString) array.items().get(0)).value(), StandardCharsets.UTF_8));
    }

    @Test
    void parsesPipelinedCommandsInOnePacket() {
        List<RespValue> values = fed(
                bytes("*1\r\n$4\r\nPING\r\n*2\r\n$4\r\nECHO\r\n$2\r\nhi\r\n")).drain();
        assertEquals(2, values.size());
    }

    @Test
    void parsesInlineCommand() {
        List<RespValue> values = fed(bytes("PING hello\r\n")).drain();
        assertEquals(1, values.size());
        RespValue.Array array = assertInstanceOf(RespValue.Array.class, values.get(0));
        assertEquals(2, array.items().size());
    }

    @Test
    void parsesNilBulkAndNilArray() {
        List<RespValue> values = fed(bytes("$-1\r\n")).drain();
        assertEquals(RespValue.nilBulk(), values.get(0));
        values = fed(bytes("*-1\r\n")).drain();
        assertEquals(RespValue.nilArray(), values.get(0));
    }

    @Test
    void parsesIntegersAndSimpleStrings() {
        List<RespValue> values = fed(bytes(":-42\r\n+OK\r\n")).drain();
        assertEquals(new RespValue.Integer(-42), values.get(0));
        assertEquals(new RespValue.SimpleString("OK"), values.get(1));
    }

    @Test
    void rejectsGarbage() {
        RespParser parser = fed(bytes("*x\r\n"));
        assertThrows(ProtocolException.class, parser::drain);
    }

    @Test
    void binarySafeBulkString() {
        byte[] payload = new byte[] {0x00, (byte) 0xFF, 0x41};
        byte[] header = bytes("*1\r\n$3\r\n");
        byte[] tail = bytes("\r\n");
        byte[] all = new byte[header.length + payload.length + tail.length];
        System.arraycopy(header, 0, all, 0, header.length);
        System.arraycopy(payload, 0, all, header.length, payload.length);
        System.arraycopy(tail, 0, all, header.length + payload.length, tail.length);
        List<RespValue> values = fed(all).drain();
        RespValue.Array array = assertInstanceOf(RespValue.Array.class, values.get(0));
        assertEquals(3, ((RespValue.BulkString) array.items().get(0)).value().length);
    }
}
