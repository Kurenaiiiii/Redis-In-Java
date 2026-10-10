package dev.redisinjava.command.sets;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisSetValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.ArrayList;
import java.util.List;

/** SMEMBERS key: all members. */
public final class SmembersCommand extends BaseCommand {
    @Override
    public String name() {
        return "smembers";
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
        List<RespValue> out = new ArrayList<>();
        if (value != null) {
            for (String member : ((RedisSetValue) value).members()) {
                out.add(bulk(member));
            }
        }
        return RespValue.array(out);
    }
}
