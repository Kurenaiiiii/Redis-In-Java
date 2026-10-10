package dev.redisinjava.command.strings;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** DECR key: decrements by one. */
public final class DecrCommand extends IncrBase {
    @Override
    public String name() {
        return "decr";
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
        return add(ctx, argv.get(1), -1);
    }
}
