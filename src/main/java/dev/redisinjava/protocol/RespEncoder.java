package dev.redisinjava.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serializes {@link RespValue} to RESP2 wire bytes. Stateless and thread-safe.
 */
public final class RespEncoder {
    private RespEncoder() {
    }

    /** Encodes a value to a freshly allocated byte array. */
    public static byte[] encode(RespValue value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            encode(value, out);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return out.toByteArray();
    }

    /** Encodes a value to the given stream. */
    public static void encode(RespValue value, OutputStream out) throws IOException {
        if (value instanceof RespValue.SimpleString s) {
            out.write('+');
            out.write(s.value().getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof RespValue.Error e) {
            out.write('-');
            out.write(e.message().getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof RespValue.Integer i) {
            out.write(':');
            out.write(Long.toString(i.value()).getBytes(StandardCharsets.US_ASCII));
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof RespValue.BulkString b) {
            if (b.value() == null) {
                out.write("$-1\r\n".getBytes(StandardCharsets.US_ASCII));
            } else {
                out.write('$');
                out.write(Integer.toString(b.value().length).getBytes(StandardCharsets.US_ASCII));
                out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                out.write(b.value());
                out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
        } else if (value instanceof RespValue.Array a) {
            if (a.items() == null) {
                out.write("*-1\r\n".getBytes(StandardCharsets.US_ASCII));
            } else {
                out.write('*');
                out.write(Integer.toString(a.items().size()).getBytes(StandardCharsets.US_ASCII));
                out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                for (RespValue item : a.items()) {
                    encode(item, out);
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown RESP value: " + value);
        }
    }
}
