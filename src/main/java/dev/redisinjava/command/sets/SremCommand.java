package dev.redisinjava.command.sets;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisSetValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** SREM key member [member ...]: removes members, returns how many existed. */
public final class SremCommand extends BaseCommand {
    @Override
    public String name() {
        return "srem";
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
        RedisValue value = typed(ctx, argv.get(1), ValueType.SET, error);
        if (error[0] != null) {
            return error[0];
        }
        if (value == null) {
            return RespValue.intVal(0);
        }
        RedisSetValue set = (RedisSetValue) value;
        int removed = 0;
        for (int i = 2; i < argv.size(); i++) {
            if (set.members().remove(argv.get(i))) {
                removed++;
            }
        }
        if (set.members().isEmpty()) {
            ctx.keySpace().delete(argv.get(1));
        }
        return RespValue.intVal(removed);
    }
}
