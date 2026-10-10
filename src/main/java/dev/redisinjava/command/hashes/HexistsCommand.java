package dev.redisinjava.command.hashes;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisHashValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** HEXISTS key field: 1 when the field exists. */
public final class HexistsCommand extends BaseCommand {
    @Override
    public String name() {
        return "hexists";
    }

    @Override
    public int arity() {
        return 3;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisValue value = typed(ctx, argv.get(1), ValueType.HASH, error);
        if (error[0] != null) {
            return error[0];
        }
        boolean exists = value != null
                && ((RedisHashValue) value).fields().containsKey(argv.get(2));
        return RespValue.intVal(exists ? 1 : 0);
    }
}
