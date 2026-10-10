package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** DEL key [key ...]: removes keys, returning how many existed. */
public final class DelCommand extends BaseCommand {
    @Override
    public String name() {
        return "del";
    }

    @Override
    public int arity() {
        return -2;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        int removed = 0;
        for (int i = 1; i < argv.size(); i++) {
            if (ctx.keySpace().delete(argv.get(i))) {
                removed++;
            }
        }
        return RespValue.intVal(removed);
    }
}
