package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/** Shared EXPIRE/PEXPIRE logic: past timestamps delete the key, return 1. */
public abstract class ExpireBase extends BaseCommand {
    protected RespValue expire(CommandContext ctx, String key, Long millis) {
        if (millis == null) {
            return notInteger();
        }
        if (!ctx.keySpace().contains(key)) {
            return RespValue.intVal(0);
        }
        if (millis <= 0) {
            ctx.keySpace().delete(key);
            return RespValue.intVal(1);
        }
        ctx.keySpace().setExpireAt(key, System.currentTimeMillis() + millis);
        return RespValue.intVal(1);
    }

    protected static Long secondsToMillis(String text) {
        Long seconds = Raw.parseLong(text);
        if (seconds == null) {
            return null;
        }
        try {
            return Math.multiplyExact(seconds, 1000);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
