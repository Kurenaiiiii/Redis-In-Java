package dev.redisinjava.command.strings;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/** INCRBY key delta: increments by an arbitrary amount. */
public final class IncrByCommand extends IncrBase {
    @Override
    public String name() {
        return "incrby";
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
        Long delta = Raw.parseLong(argv.get(2));
        if (delta == null) {
            return notInteger();
        }
        return add(ctx, argv.get(1), delta);
    }
}
