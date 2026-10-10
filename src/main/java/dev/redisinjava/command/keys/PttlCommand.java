package dev.redisinjava.command.keys;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** PTTL key: remaining milliseconds. */
public final class PttlCommand extends TtlBase {
    @Override
    public String name() {
        return "pttl";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return ttl(ctx, argv.get(1), true);
    }
}
