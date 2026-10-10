package dev.redisinjava.command.sets;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisSetValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import java.util.List;

/** SADD key member [member ...]: adds members, returns new ones added. */
public final class SaddCommand extends BaseCommand {
    @Override
    public String name() {
        return "sadd";
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
        RedisValue current = typed(ctx, argv.get(1), ValueType.SET, error);
        if (error[0] != null) {
            return error[0];
        }
        RedisSetValue set;
        if (current == null) {
            set = new RedisSetValue();
            ctx.keySpace().put(argv.get(1), set);
        } else {
            set = (RedisSetValue) current;
        }
        int added = 0;
        for (int i = 2; i < argv.size(); i++) {
            if (set.members().add(argv.get(i))) {
                added++;
            }
        }
        return RespValue.intVal(added);
    }
}
