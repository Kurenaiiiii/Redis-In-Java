package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** PERSIST key: removes the TTL, 1 when one existed. */
public final class PersistCommand extends BaseCommand {
    @Override
    public String name() {
        return "persist";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return RespValue.intVal(ctx.keySpace().persist(argv.get(1)) ? 1 : 0);
    }
}
