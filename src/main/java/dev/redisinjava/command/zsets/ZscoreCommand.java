package dev.redisinjava.command.zsets;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisZSetValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/** ZSCORE key member: the score, or nil. */
public final class ZscoreCommand extends ZsetBase {
    @Override
    public String name() {
        return "zscore";
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
        return score == null ? RespValue.nilBulk() : bulk(Raw.formatDouble(score));
    }
}
