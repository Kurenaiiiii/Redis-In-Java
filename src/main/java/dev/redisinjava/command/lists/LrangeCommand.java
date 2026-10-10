package dev.redisinjava.command.lists;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisListValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;
import java.util.ArrayList;
import java.util.List;

/** LRANGE key start stop: slice with negative indexes, like real Redis. */
public final class LrangeCommand extends BaseCommand {
    @Override
    public String name() {
        return "lrange";
    }

    @Override
    public int arity() {
        return 4;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        Long from = Raw.parseLong(argv.get(2));
        Long to = Raw.parseLong(argv.get(3));
        if (from == null || to == null) {
            return notInteger();
        }
        RespValue[] error = new RespValue[1];
        RedisValue value = typed(ctx, argv.get(1), ValueType.LIST, error);
        if (error[0] != null) {
            return error[0];
        }
        if (value == null) {
            return RespValue.array(List.of());
        }
        List<byte[]> items = new ArrayList<>(((RedisListValue) value).items());
        long size = items.size();
        long start = from < 0 ? size + from : from;
        long stop = to < 0 ? size + to : to;
        start = Math.max(start, 0);
        stop = Math.min(stop, size - 1);
        List<RespValue> out = new ArrayList<>();
        for (long i = start; i <= stop && i < size; i++) {
            out.add(RespValue.bulk(items.get((int) i)));
        }
        return RespValue.array(out);
    }
}
