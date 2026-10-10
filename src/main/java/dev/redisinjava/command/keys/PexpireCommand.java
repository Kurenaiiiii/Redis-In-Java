package dev.redisinjava.command.keys;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/** PEXPIRE key milliseconds: TTL in milliseconds. */
public final class PexpireCommand extends ExpireBase {
    @Override
    public String name() {
        return "pexpire";
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
        return expire(ctx, argv.get(1), Raw.parseLong(argv.get(2)));
    }
}
