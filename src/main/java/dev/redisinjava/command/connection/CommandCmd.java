package dev.redisinjava.command.connection;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.Command;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.ArrayList;
import java.util.List;

/**
 * COMMAND [COUNT]: minimal reply listing each command's name and arity.
 * Full COMMAND DOCS output is out of scope; redis-cli works without it.
 */
public final class CommandCmd extends BaseCommand {
    @Override
    public String name() {
        return "command";
    }

    @Override
    public int arity() {
        return -1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (argv.size() == 2 && argv.get(1).equalsIgnoreCase("count")) {
            return RespValue.intVal(ctx.registry().all().size());
        }
        if (argv.size() > 1) {
            return RespValue.err("ERR unknown subcommand '"
                    + argv.get(1) + "'. Try COMMAND.");
        }
        List<RespValue> out = new ArrayList<>();
        for (Command command : ctx.registry().all()) {
            out.add(RespValue.array(bulk(command.name()),
                    RespValue.intVal(command.arity())));
        }
        return RespValue.array(out);
    }
}
