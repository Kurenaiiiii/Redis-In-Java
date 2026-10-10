package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/**
 * PEXPIREAT key milliseconds-timestamp: absolute expiry in epoch millis.
 * Exists mainly so AOF rewrites can pin exact TTLs; also usable directly.
 */
public final class PexpireatCommand extends BaseCommand {
    @Override
    public String name() {
        return "pexpireat";
    }

    @Override
    public int arity() {
        return 3;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        Long at = Raw.parseLong(argv.get(2));
        if (at == null) {
            return notInteger();
        }
        if (!ctx.keySpace().contains(argv.get(1))) {
            return RespValue.intVal(0);
        }
        if (at <= System.currentTimeMillis()) {
            ctx.keySpace().delete(argv.get(1));
            return RespValue.intVal(1);
        }
        ctx.keySpace().setExpireAt(argv.get(1), at);
        return RespValue.intVal(1);
    }
}
