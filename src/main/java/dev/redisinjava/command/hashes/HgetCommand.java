package dev.redisinjava.command.hashes;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisHashValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** HGET key field: the field value or nil. */
public final class HgetCommand extends BaseCommand {
    @Override
    public String name() {
        return "hget";
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
        if (value == null) {
            return RespValue.nilBulk();
        }
        byte[] field = ((RedisHashValue) value).fields().get(argv.get(2));
        return field == null ? RespValue.nilBulk() : RespValue.bulk(field);
    }
}
