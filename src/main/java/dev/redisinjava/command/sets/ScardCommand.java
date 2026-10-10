package dev.redisinjava.command.sets;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisSetValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** SCARD key: member count, 0 when missing. */
public final class ScardCommand extends BaseCommand {
    @Override
    public String name() {
        return "scard";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisValue value = typed(ctx, argv.get(1), ValueType.SET, error);
        if (error[0] != null) {
            return error[0];
        }
        return RespValue.intVal(value == null ? 0 : ((RedisSetValue) value).members().size());
    }
}
