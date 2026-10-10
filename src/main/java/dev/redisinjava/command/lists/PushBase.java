package dev.redisinjava.command.lists;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisListValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;
import java.util.List;

/** Shared push logic: creates the list on first push. */
public abstract class PushBase extends BaseCommand {
    protected RespValue push(CommandContext ctx, String key, List<String> elements,
                             boolean left) {
        RespValue[] error = new RespValue[1];
        RedisValue current = typed(ctx, key, ValueType.LIST, error);
        if (error[0] != null) {
            return error[0];
        }
        RedisListValue list;
        if (current == null) {
            list = new RedisListValue();
            ctx.keySpace().put(key, list);
        } else {
            list = (RedisListValue) current;
        }
        for (String element : elements) {
            if (left) {
                list.items().addFirst(Raw.encode(element));
            } else {
                list.items().addLast(Raw.encode(element));
            }
        }
        return RespValue.intVal(list.items().size());
    }
}
