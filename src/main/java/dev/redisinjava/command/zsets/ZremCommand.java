package dev.redisinjava.command.zsets;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisZSetValue;
import java.util.List;

/** ZREM key member [member ...]: removes members, returns how many existed. */
public final class ZremCommand extends ZsetBase {
    @Override
    public String name() {
        return "zrem";
    }

    @Override
    public int arity() {
        return -3;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisZSetValue zset = lookup(ctx, argv.get(1), error);
        if (error[0] != null) {
            return error[0];
        }
        if (zset == null) {
            return RespValue.intVal(0);
        }
        int removed = 0;
        for (int i = 2; i < argv.size(); i++) {
            if (zset.remove(argv.get(i))) {
                removed++;
            }
        }
        if (zset.scores().isEmpty()) {
            ctx.keySpace().delete(argv.get(1));
        }
        return RespValue.intVal(removed);
    }
}
