package dev.redisinjava.command.zsets;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisZSetValue;
import java.util.List;

/** ZRANK key member: 0-based rank, or nil. */
public final class ZrankCommand extends ZsetBase {
    @Override
    public String name() {
        return "zrank";
    }

    @Override
    public int arity() {
        return 3;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisZSetValue zset = lookup(ctx, argv.get(1), error);
        if (error[0] != null) {
            return error[0];
        }
        if (zset == null) {
            return RespValue.nilBulk();
        }
        Double score = zset.scores().get(argv.get(2));
        if (score == null) {
            return RespValue.nilBulk();
        }
        return RespValue.intVal(zset.skiplist().rank(score, argv.get(2)));
    }
}
