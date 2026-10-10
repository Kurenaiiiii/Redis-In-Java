package dev.redisinjava.command.connection;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** ECHO message: returns the message back as a bulk string. */
public final class EchoCommand extends BaseCommand {
    @Override
    public String name() {
        return "echo";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return bulk(argv.get(1));
    }
}
