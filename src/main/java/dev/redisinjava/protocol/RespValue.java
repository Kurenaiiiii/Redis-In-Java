package dev.redisinjava.protocol;

import java.util.List;

/**
 * A RESP2 value. All command arguments arrive as bulk strings; replies use
 * the full RESP2 type set. Bulk string payloads are raw bytes so the protocol
 * layer stays binary-safe; the command layer decodes them with ISO-8859-1
 * (one char per byte, fully reversible).
 */
public sealed interface RespValue permits
        RespValue.SimpleString, RespValue.Error, RespValue.Integer,
        RespValue.BulkString, RespValue.Array {

    record SimpleString(String value) implements RespValue {}

    record Error(String message) implements RespValue {}

    record Integer(long value) implements RespValue {}

    /** Null payload means RESP nil ($-1). */
    record BulkString(byte[] value) implements RespValue {}

    /** Null items means RESP nil array (*-1). */
    record Array(List<RespValue> items) implements RespValue {}

    static RespValue ok() {
        return new SimpleString("OK");
    }

    static RespValue pong() {
        return new SimpleString("PONG");
    }

    static RespValue queued() {
        return new SimpleString("QUEUED");
    }

    static RespValue err(String message) {
        return new Error(message);
    }

    static RespValue intVal(long value) {
        return new Integer(value);
    }

    static RespValue bulk(byte[] value) {
        return new BulkString(value);
    }

    static RespValue nilBulk() {
        return new BulkString(null);
    }

    static RespValue nilArray() {
        return new Array(null);
    }

    static RespValue array(List<RespValue> items) {
        return new Array(items);
    }

    static RespValue array(RespValue... items) {
        return new Array(List.of(items));
    }
}
