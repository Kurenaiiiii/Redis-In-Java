package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** DBSIZE: number of live keys. */
public final class DbsizeCommand extends BaseCommand {
    @Override
    public String name() {
        return "dbsize";
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return RespValue.intVal(ctx.keySpace().size());
    }
}
