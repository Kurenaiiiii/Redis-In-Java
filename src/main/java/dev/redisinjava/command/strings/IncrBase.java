package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisStringValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;
import java.util.List;

/** Shared INCR/DECR/INCRBY logic: missing keys start at zero. */
public abstract class IncrBase extends BaseCommand {
    protected RespValue add(CommandContext ctx, String key, long delta) {
        RedisValue current = ctx.keySpace().get(key);
        if (current != null && current.type() != ValueType.STRING) {
            return wrongType();
        }
        long base = 0;
        if (current != null) {
            Long parsed = Raw.parseLong(Raw.decode(((RedisStringValue) current).data()));
            if (parsed == null) {
                return notInteger();
            }
            base = parsed;
        }
        long next;
        try {
            next = Math.addExact(base, delta);
        } catch (ArithmeticException overflow) {
            return RespValue.err("ERR increment or decrement would overflow");
        }
        // put() preserves any TTL, like real INCR.
        ctx.keySpace().put(key,
                new RedisStringValue(Raw.encode(Long.toString(next))));
        return RespValue.intVal(next);
    }
}
