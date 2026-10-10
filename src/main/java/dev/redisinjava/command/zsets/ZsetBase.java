package dev.redisinjava.command.zsets;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.RedisZSetValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;
import java.util.List;

/**
 * Shared sorted-set lookup: fetches the key and enforces the zset type.
 */
public abstract class ZsetBase extends BaseCommand {
    protected static RedisZSetValue lookup(CommandContext ctx, String key,
                                           RespValue[] error) {
        RedisValue value = ctx.keySpace().get(key);
        if (value == null) {
            return null;
        }
        if (value.type() != ValueType.ZSET) {
            error[0] = wrongType();
            return null;
        }
        return (RedisZSetValue) value;
    }

    protected static RespValue invalidFloat() {
        return RespValue.err("ERR value is not a valid float");
    }

    /** Parses a score bound, accepting -inf/+inf and exclusive '(' prefixes. */
    protected static Bound parseBound(String text) {
        boolean exclusive = text.startsWith("(");
        String number = exclusive ? text.substring(1) : text;
        Double value = Raw.parseDouble(number);
        if (value == null) {
            return null;
        }
        return new Bound(value, exclusive);
    }

    protected record Bound(double value, boolean exclusive) {
    }
}
