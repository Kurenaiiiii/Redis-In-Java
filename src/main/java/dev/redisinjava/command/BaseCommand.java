package dev.redisinjava.command;

import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;

/** Shared helpers so individual commands stay a few lines long. */
public abstract class BaseCommand implements Command {
    protected static RespValue wrongArity(String name) {
        return RespValue.err("ERR wrong number of arguments for '" + name + "' command");
    }

    protected static RespValue wrongType() {
        return RespValue.err(
                "WRONGTYPE Operation against a key holding the wrong kind of value");
    }

    protected static RespValue notInteger() {
        return RespValue.err("ERR value is not an integer or out of range");
    }

    protected static RespValue syntaxError() {
        return RespValue.err("ERR syntax error");
    }

    public static RespValue bulk(String text) {
        return RespValue.bulk(Raw.encode(text));
    }

    /** Fetches a key and checks its type; null key or null out means "reply now". */
    protected static RedisValue typed(CommandContext ctx, String key, ValueType want,
                                      RespValue[] errorOut) {
        RedisValue value = ctx.keySpace().get(key);
        if (value == null) {
            return null;
        }
        if (value.type() != want) {
            errorOut[0] = wrongType();
            return null;
        }
        return value;
    }
}
