package dev.redisinjava.command.zsets;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisZSetValue;
import dev.redisinjava.store.SkipList;
import dev.redisinjava.util.Raw;
import java.util.ArrayList;
import java.util.List;

/** ZRANGE key start stop [WITHSCORES]: index slice in score order. */
public final class ZrangeCommand extends ZsetBase {
    @Override
    public String name() {
        return "zrange";
    }

    @Override
    public int arity() {
        return -4;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (argv.size() > 5) {
            return wrongArity(name());
        }
        boolean withScores = false;
        if (argv.size() == 5) {
            if (!argv.get(4).equalsIgnoreCase("withscores")) {
                return syntaxError();
            }
            withScores = true;
        }
        Long from = Raw.parseLong(argv.get(2));
        Long to = Raw.parseLong(argv.get(3));
        if (from == null || to == null) {
            return notInteger();
        }
        RespValue[] error = new RespValue[1];
        RedisZSetValue zset = lookup(ctx, argv.get(1), error);
        if (error[0] != null) {
            return error[0];
        }
        List<RespValue> out = new ArrayList<>();
        if (zset != null) {
            for (SkipList.Entry entry : zset.skiplist().rangeByRank(from, to)) {
                out.add(bulk(entry.member()));
                if (withScores) {
                    out.add(bulk(Raw.formatDouble(entry.score())));
                }
            }
        }
        return RespValue.array(out);
    }
}
