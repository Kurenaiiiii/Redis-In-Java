package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisStringValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.ArrayList;
import java.util.List;

/** MGET key [key ...]: values in order, nil for missing keys. */
public final class MgetCommand extends BaseCommand {
    @Override
    public String name() {
        return "mget";
    }

    @Override
    public int arity() {
        return -2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        List<RespValue> out = new ArrayList<>(argv.size() - 1);
        for (int i = 1; i < argv.size(); i++) {
            RedisValue value = ctx.keySpace().get(argv.get(i));
            if (value == null) {
                out.add(RespValue.nilBulk());
            } else if (value.type() != ValueType.STRING) {
                return wrongType();
            } else {
                out.add(RespValue.bulk(((RedisStringValue) value).data()));
            }
        }
        return RespValue.array(out);
    }
}
