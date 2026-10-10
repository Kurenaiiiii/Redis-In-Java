package dev.redisinjava.command.sets;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisSetValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** SISMEMBER key member: 1 when present. */
public final class SismemberCommand extends BaseCommand {
    @Override
    public String name() {
        return "sismember";
    }

    @Override
    public int arity() {
        return 3;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisValue value = typed(ctx, argv.get(1), ValueType.SET, error);
        if (error[0] != null) {
            return error[0];
        }
        boolean member = value != null
                && ((RedisSetValue) value).members().contains(argv.get(2));
        return RespValue.intVal(member ? 1 : 0);
    }
}
