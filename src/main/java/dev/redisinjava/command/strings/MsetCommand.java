package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisStringValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/** MSET key value [key value ...]: sets many keys atomically. */
public final class MsetCommand extends BaseCommand {
    @Override
    public String name() {
        return "mset";
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
        if ((argv.size() - 1) % 2 != 0) {
            return wrongArity(name());
        }
        for (int i = 1; i < argv.size(); i += 2) {
            ctx.keySpace().overwrite(argv.get(i),
                    new RedisStringValue(Raw.encode(argv.get(i + 1))));
        }
        return RespValue.ok();
    }
}
