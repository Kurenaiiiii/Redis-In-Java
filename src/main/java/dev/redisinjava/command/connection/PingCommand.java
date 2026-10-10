package dev.redisinjava.command.connection;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** PING [message]: liveness check; echoes the argument back when given. */
public final class PingCommand extends BaseCommand {
    @Override
    public String name() {
        return "ping";
    }

    @Override
    public int arity() {
        return -1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (argv.size() > 2) {
            return wrongArity(name());
        }
        if (ctx.connection().isSubscribed()) {
            // RESP array form, like real Redis inside subscribed mode.
            String echo = argv.size() == 2 ? argv.get(1) : "";
            return RespValue.array(bulk("pong"), bulk(echo));
        }
        if (argv.size() == 2) {
            return bulk(argv.get(1));
        }
        return RespValue.pong();
    }
}
