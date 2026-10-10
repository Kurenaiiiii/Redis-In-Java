package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisStringValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** GET key: the string value, or nil when missing. */
public final class GetCommand extends BaseCommand {
    @Override
    public String name() {
        return "get";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisValue value = typed(ctx, argv.get(1), ValueType.STRING, error);
        if (error[0] != null) {
            return error[0];
        }
        if (value == null) {
            return RespValue.nilBulk();
        }
        return RespValue.bulk(((RedisStringValue) value).data());
    }
}
