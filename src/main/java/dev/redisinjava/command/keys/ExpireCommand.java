package dev.redisinjava.command.keys;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** EXPIRE key seconds: TTL in seconds. */
public final class ExpireCommand extends ExpireBase {
    @Override
    public String name() {
        return "expire";
    }

    @Override
    public int arity() {
        return 3;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return expire(ctx, argv.get(1), secondsToMillis(argv.get(2)));
    }
}
