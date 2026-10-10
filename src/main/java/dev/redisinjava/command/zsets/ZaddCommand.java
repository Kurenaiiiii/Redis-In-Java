package dev.redisinjava.command.zsets;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisZSetValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/** ZADD key score member [score member ...]: returns members newly added. */
public final class ZaddCommand extends ZsetBase {
    @Override
    public String name() {
        return "zadd";
    }

    @Override
    public int arity() {
        return -4;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if ((argv.size() - 2) % 2 != 0) {
            return wrongArity(name());
        }
        // Parse everything before mutating, so bad input changes nothing.
        int pairs = (argv.size() - 2) / 2;
        double[] scores = new double[pairs];
        for (int i = 0; i < pairs; i++) {
            Double score = Raw.parseDouble(argv.get(2 + i * 2));
            if (score == null) {
                return invalidFloat();
            }
            scores[i] = score;
        }
        RespValue[] error = new RespValue[1];
        RedisZSetValue zset = lookup(ctx, argv.get(1), error);
        if (error[0] != null) {
            return error[0];
        }
        if (zset == null) {
            zset = new RedisZSetValue();
            ctx.keySpace().put(argv.get(1), zset);
        }
        int added = 0;
        for (int i = 0; i < pairs; i++) {
            if (zset.add(argv.get(3 + i * 2), scores[i])) {
                added++;
            }
        }
        return RespValue.intVal(added);
    }
}
