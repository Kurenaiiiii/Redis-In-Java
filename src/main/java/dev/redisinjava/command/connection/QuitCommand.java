package dev.redisinjava.command.connection;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** QUIT: replies +OK and closes the connection after the reply drains. */
public final class QuitCommand extends BaseCommand {
    @Override
    public String name() {
        return "quit";
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        ctx.connection().closeAfterDrain();
        return RespValue.ok();
    }
}
