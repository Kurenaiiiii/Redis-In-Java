package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** TTL key / PTTL key: remaining TTL, -1 persistent, -2 missing. */
public abstract class TtlBase extends BaseCommand {
    protected RespValue ttl(CommandContext ctx, String key, boolean millis) {
        long at = ctx.keySpace().expireAt(key);
        if (at == -2) {
            return RespValue.intVal(-2);
        }
        if (at < 0) {
            return RespValue.intVal(-1);
        }
        long left = at - System.currentTimeMillis();
        if (left < 0) {
            ctx.keySpace().delete(key);
            return RespValue.intVal(-2);
        }
        return RespValue.intVal(millis ? left : left / 1000);
    }
}
