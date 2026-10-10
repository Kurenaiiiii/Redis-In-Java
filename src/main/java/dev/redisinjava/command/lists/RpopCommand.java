package dev.redisinjava.command.lists;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** RPOP key [count]: removes and returns from the tail. */
public final class RpopCommand extends PopBase {
    @Override
    public String name() {
        return "rpop";
    }

    @Override
    public int arity() {
        return -2;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return pop(ctx, argv, false);
    }
}
