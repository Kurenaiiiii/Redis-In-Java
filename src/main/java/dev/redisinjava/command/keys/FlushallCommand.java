package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** FLUSHALL: wipes the dataset. An optional ASYNC/SYNC argument is ignored. */
public final class FlushallCommand extends BaseCommand {
    @Override
    public String name() {
        return "flushall";
    }

    @Override
    public int arity() {
        return -1;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (argv.size() > 2) {
            return wrongArity(name());
        }
        ctx.keySpace().clear();
        return RespValue.ok();
    }
}
