package dev.redisinjava.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Incremental RESP2 parser. Bytes from the socket are appended with
 * {@link #feed}; {@link #drain} returns every complete value parsed so far
 * and keeps any partial tail buffered, so a command split across TCP packets
 * and several pipelined commands in one packet both work.
 *
 * <p>Plain lines (telnet-style inline commands) are also accepted and turned
 * into an array of bulk strings. Malformed input raises
 * {@link ProtocolException}; the server then replies with an error and closes
 * the connection, like real Redis does on protocol errors.
 */
public final class RespParser {
    private static final int MAX_DEPTH = 32;

    private byte[] buf = new byte[4096];
    private int start;
    private int end;

    /** Appends freshly read socket bytes. */
    public void feed(byte[] src, int off, int len) {
        ensureCapacity(len);
        if (start > 0 && (long) start + (long) (end - start) + len > buf.length) {
            System.arraycopy(buf, start, buf, 0, end - start);
            end -= start;
            start = 0;
        }
        ensureCapacity(len);
        System.arraycopy(src, off, buf, end, len);
        end += len;
    }

    /** Parses and returns all complete values; partial data stays buffered. */
    public List<RespValue> drain() {
        List<RespValue> out = new ArrayList<>();
        while (true) {
            if (start >= end) {
                break;
            }
            int mark = start;
            RespValue value = parseOne(0);
            if (value == null) {
                start = mark;
                break;
            }
            out.add(value);
        }
        if (start >= end) {
            start = 0;
            end = 0;
        }
        return out;
    }

    /** True when no unparsed bytes remain. */
    public boolean isEmpty() {
        return start >= end;
    }

    private RespValue parseOne(int depth) {
        if (depth > MAX_DEPTH) {
            throw new ProtocolException("ERR Protocol error: nesting too deep");
        }
        if (start >= end) {
            return null;
        }
        byte type = buf[start];
        if (type == '*') {
            return parseArray(depth);
        }
        if (type == '$' || type == ':' || type == '+' || type == '-') {
            RespValue scalar = parseScalar(depth);
            return scalar;
        }
        return parseInline();
    }

    private RespValue parseArray(int depth) {
        long count = readLineLong(1);
        if (count == Long.MIN_VALUE) {
            return null; // no full line yet
        }
        if (count < -1 || count > 1024 * 1024) {
            throw new ProtocolException("ERR Protocol error: invalid multibulk length");
        }
        if (count == -1) {
            return RespValue.nilArray();
        }
        int saved = start;
        List<RespValue> items = new ArrayList<>((int) Math.min(count, 16));
        for (long i = 0; i < count; i++) {
            if (start >= end) {
                start = saved;
                return null;
            }
            if (buf[start] != '$') {
                throw new ProtocolException("ERR Protocol error: expected '$', got '"
                        + (char) buf[start] + "'");
            }
            RespValue item = parseScalar(depth + 1);
            if (item == null) {
                start = saved;
                return null;
            }
            if (!(item instanceof RespValue.BulkString)) {
                throw new ProtocolException("ERR Protocol error: expected bulk string in multibulk");
            }
            items.add(item);
        }
        return RespValue.array(items);
    }

    private RespValue parseScalar(int depth) {
        byte type = buf[start];
        int lineEnd = findCrlf(start);
        if (lineEnd < 0) {
            return null;
        }
        if (type == '+' || type == '-' || type == ':') {
            String text = new String(buf, start + 1, lineEnd - start - 1,
                    java.nio.charset.StandardCharsets.UTF_8);
            start = lineEnd + 2;
            if (type == '+') {
                return new RespValue.SimpleString(text);
            }
            if (type == '-') {
                return new RespValue.Error(text);
            }
            try {
                return new RespValue.Integer(Long.parseLong(text));
            } catch (NumberFormatException bad) {
                throw new ProtocolException("ERR Protocol error: invalid integer '" + text + "'");
            }
        }
        // '$': bulk string
        String lenText = new String(buf, start + 1, lineEnd - start - 1,
                java.nio.charset.StandardCharsets.US_ASCII);
        long len;
        try {
            len = Long.parseLong(lenText);
        } catch (NumberFormatException bad) {
            throw new ProtocolException("ERR Protocol error: invalid bulk length '" + lenText + "'");
        }
        if (len == -1) {
            start = lineEnd + 2;
            return RespValue.nilBulk();
        }
        if (len < -1 || len > 512 * 1024 * 1024) {
            throw new ProtocolException("ERR Protocol error: invalid bulk length '" + lenText + "'");
        }
        int dataStart = lineEnd + 2;
        if ((long) end - dataStart < len + 2) {
            return null; // wait for the rest of the payload
        }
        byte[] data = new byte[(int) len];
        System.arraycopy(buf, dataStart, data, 0, (int) len);
        if (buf[dataStart + (int) len] != '\r' || buf[dataStart + (int) len + 1] != '\n') {
            throw new ProtocolException("ERR Protocol error: bulk string missing CRLF");
        }
        start = dataStart + (int) len + 2;
        return new RespValue.BulkString(data);
    }

    private RespValue parseInline() {
        int lineEnd = findCrlf(start);
        if (lineEnd < 0) {
            // tolerate a lone LF terminated line too
            int lf = findLf(start);
            if (lf < 0) {
                return null;
            }
            lineEnd = lf;
            String text = new String(buf, start, lineEnd - start,
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            start = lineEnd + 1;
            return inlineValue(text);
        }
        String text = new String(buf, start, lineEnd - start,
                java.nio.charset.StandardCharsets.UTF_8).trim();
        start = lineEnd + 2;
        return inlineValue(text);
    }

    private static RespValue inlineValue(String text) {
        if (text.isEmpty()) {
            return null; // blank line: ignore, wait for more input
        }
        String[] parts = text.split("[ \\t]+");
        List<RespValue> items = new ArrayList<>(parts.length);
        for (String part : parts) {
            items.add(new RespValue.BulkString(
                    part.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return RespValue.array(items);
    }

    /**
     * Reads a line starting at {@code start + skip} and parses the rest as a
     * long. Returns {@link Long#MIN_VALUE} when no full line is buffered yet.
     * Advances {@link #start} past the line.
     */
    private long readLineLong(int skip) {
        int lineEnd = findCrlf(start);
        if (lineEnd < 0) {
            return Long.MIN_VALUE;
        }
        String text = new String(buf, start + skip, lineEnd - start - skip,
                java.nio.charset.StandardCharsets.US_ASCII);
        start = lineEnd + 2;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException bad) {
            throw new ProtocolException("ERR Protocol error: invalid multibulk length '" + text + "'");
        }
    }

    private int findCrlf(int from) {
        for (int i = from; i + 1 < end; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private int findLf(int from) {
        for (int i = from; i < end; i++) {
            if (buf[i] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private void ensureCapacity(int extra) {
        if ((long) (end - start) + extra <= buf.length) {
            return;
        }
        int size = buf.length;
        while ((long) (end - start) + extra > size) {
            size *= 2;
        }
        byte[] grown = new byte[size];
        System.arraycopy(buf, start, grown, 0, end - start);
        end -= start;
        start = 0;
        buf = grown;
    }
}
