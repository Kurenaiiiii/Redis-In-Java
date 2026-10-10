package dev.redisinjava.command.lists;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** LPUSH key el [el ...]: prepends elements, returns the new length. */
public final class LpushCommand extends PushBase {
    @Override
    public String name() {
        return "lpush";
    }

    @Override
    public int arity() {
        return -3;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        return push(ctx, argv.get(1), argv.subList(2, argv.size()), true);
    }
}
