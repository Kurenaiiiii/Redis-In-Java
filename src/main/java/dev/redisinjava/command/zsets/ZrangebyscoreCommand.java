package dev.redisinjava.command.zsets;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisZSetValue;
import dev.redisinjava.store.SkipList;
import dev.redisinjava.util.Raw;
import java.util.ArrayList;
import java.util.List;

/**
 * ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT offset count].
 * Bounds accept -inf/+inf and exclusive "(1" prefixes.
 */
public final class ZrangebyscoreCommand extends ZsetBase {
    @Override
    public String name() {
        return "zrangebyscore";
    }

    @Override
    public int arity() {
        return -4;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        Bound min = parseBound(argv.get(2));
        Bound max = parseBound(argv.get(3));
        if (min == null || max == null) {
            return RespValue.err("ERR min or max is not a float");
        }
        boolean withScores = false;
        long offset = 0;
        long limit = -1;
        for (int i = 4; i < argv.size(); i++) {
            if (argv.get(i).equalsIgnoreCase("withscores")) {
                withScores = true;
            } else if (argv.get(i).equalsIgnoreCase("limit") && i + 2 < argv.size()) {
                Long off = Raw.parseLong(argv.get(++i));
                Long count = Raw.parseLong(argv.get(++i));
                if (off == null || count == null) {
                    return notInteger();
                }
                offset = Math.max(off, 0);
                limit = count;
            } else {
                return syntaxError();
            }
        }
        RespValue[] error = new RespValue[1];
        RedisZSetValue zset = lookup(ctx, argv.get(1), error);
        if (error[0] != null) {
            return error[0];
        }
        List<RespValue> out = new ArrayList<>();
        if (zset != null) {
            List<SkipList.Entry> entries = zset.skiplist().rangeByScore(
                    min.value(), min.exclusive(), max.value(), max.exclusive(), offset, limit);
            for (SkipList.Entry entry : entries) {
                out.add(bulk(entry.member()));
                if (withScores) {
                    out.add(bulk(Raw.formatDouble(entry.score())));
                }
            }
        }
        return RespValue.array(out);
    }
}
