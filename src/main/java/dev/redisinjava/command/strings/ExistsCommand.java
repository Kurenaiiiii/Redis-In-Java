package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** EXISTS key [key ...]: counts the keys that exist (expired ones don't). */
public final class ExistsCommand extends BaseCommand {
    @Override
    public String name() {
        return "exists";
    }

    @Override
    public int arity() {
        return -2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        int found = 0;
        for (int i = 1; i < argv.size(); i++) {
            if (ctx.keySpace().contains(argv.get(i))) {
                found++;
            }
        }
        return RespValue.intVal(found);
    }
}
