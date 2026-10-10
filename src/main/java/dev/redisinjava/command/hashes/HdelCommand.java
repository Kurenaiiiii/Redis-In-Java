package dev.redisinjava.command.hashes;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisHashValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** HDEL key field [field ...]: removes fields, returns how many existed. */
public final class HdelCommand extends BaseCommand {
    @Override
    public String name() {
        return "hdel";
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
        RedisValue value = typed(ctx, argv.get(1), ValueType.HASH, error);
        if (error[0] != null) {
            return error[0];
        }
        if (value == null) {
            return RespValue.intVal(0);
        }
        RedisHashValue hash = (RedisHashValue) value;
        int removed = 0;
        for (int i = 2; i < argv.size(); i++) {
            if (hash.fields().remove(argv.get(i)) != null) {
                removed++;
            }
        }
        if (hash.fields().isEmpty()) {
            ctx.keySpace().delete(argv.get(1));
        }
        return RespValue.intVal(removed);
    }
}
