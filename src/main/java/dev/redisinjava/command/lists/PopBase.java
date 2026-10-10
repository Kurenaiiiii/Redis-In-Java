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

/** Shared pop logic: LPOP/RPOP with optional count. */
public abstract class PopBase extends BaseCommand {
    protected RespValue pop(CommandContext ctx, List<String> argv, boolean left) {
        if (argv.size() > 3) {
            return wrongArity(argv.get(0).toLowerCase());
        }
        Long count = null;
        if (argv.size() == 3) {
            count = Raw.parseLong(argv.get(2));
            if (count == null) {
                return notInteger();
            }
            if (count < 0) {
                return RespValue.err("ERR value is not an integer or out of range");
            }
        }
        RespValue[] error = new RespValue[1];
        RedisValue current = typed(ctx, argv.get(1), ValueType.LIST, error);
        if (error[0] != null) {
            return error[0];
        }
        if (current == null) {
            return count == null ? RespValue.nilBulk() : RespValue.array(List.of());
        }
        RedisListValue list = (RedisListValue) current;
        if (count == null) {
            byte[] element = left ? list.items().pollFirst() : list.items().pollLast();
            dropIfEmpty(ctx, argv.get(1), list);
            return element == null ? RespValue.nilBulk() : RespValue.bulk(element);
        }
        List<RespValue> out = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            byte[] element = left ? list.items().pollFirst() : list.items().pollLast();
            if (element == null) {
                break;
            }
            out.add(RespValue.bulk(element));
        }
        dropIfEmpty(ctx, argv.get(1), list);
        return RespValue.array(out);
    }

    private static void dropIfEmpty(CommandContext ctx, String key, RedisListValue list) {
        if (list.items().isEmpty()) {
            ctx.keySpace().delete(key);
        }
    }
}
