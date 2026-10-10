package dev.redisinjava.command.hashes;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisHashValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;
import java.util.List;

/** HSET key field value [field value ...]: returns new fields added. */
public final class HsetCommand extends BaseCommand {
    @Override
    public String name() {
        return "hset";
    }

    @Override
    public int arity() {
        return -4;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if ((argv.size() - 2) % 2 != 0) {
            return wrongArity(name());
        }
        RespValue[] error = new RespValue[1];
        RedisValue current = typed(ctx, argv.get(1), ValueType.HASH, error);
        if (error[0] != null) {
            return error[0];
        }
        RedisHashValue hash;
        if (current == null) {
            hash = new RedisHashValue();
            ctx.keySpace().put(argv.get(1), hash);
        } else {
            hash = (RedisHashValue) current;
        }
        int added = 0;
        for (int i = 2; i < argv.size(); i += 2) {
            if (!hash.fields().containsKey(argv.get(i))) {
                added++;
            }
            hash.fields().put(argv.get(i), Raw.encode(argv.get(i + 1)));
        }
        return RespValue.intVal(added);
    }
}
