package dev.redisinjava.command.zsets;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisZSetValue;
import java.util.List;

/** ZCARD key: member count, 0 when missing. */
public final class ZcardCommand extends ZsetBase {
    @Override
    public String name() {
        return "zcard";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisZSetValue zset = lookup(ctx, argv.get(1), error);
        if (error[0] != null) {
            return error[0];
        }
        return RespValue.intVal(zset == null ? 0 : zset.scores().size());
    }
}
