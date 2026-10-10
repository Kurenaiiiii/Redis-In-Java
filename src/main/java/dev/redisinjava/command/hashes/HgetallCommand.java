package dev.redisinjava.command.hashes;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisHashValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HGETALL key: flat field,value array. */
public final class HgetallCommand extends BaseCommand {
    @Override
    public String name() {
        return "hgetall";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisValue value = typed(ctx, argv.get(1), ValueType.HASH, error);
        if (error[0] != null) {
            return error[0];
        }
        List<RespValue> out = new ArrayList<>();
        if (value != null) {
            for (Map.Entry<String, byte[]> entry : ((RedisHashValue) value).fields().entrySet()) {
                out.add(RespValue.bulk(Raw.encode(entry.getKey())));
                out.add(RespValue.bulk(entry.getValue()));
            }
        }
        return RespValue.array(out);
    }
}
