package dev.redisinjava.command.strings;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** INCR key: increments by one. */
public final class IncrCommand extends IncrBase {
    @Override
    public String name() {
        return "incr";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return add(ctx, argv.get(1), 1);
    }
}
