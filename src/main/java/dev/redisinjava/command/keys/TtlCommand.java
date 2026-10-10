package dev.redisinjava.command.keys;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** TTL key: remaining seconds. */
public final class TtlCommand extends TtlBase {
    @Override
    public String name() {
        return "ttl";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return ttl(ctx, argv.get(1), false);
    }
}
