package dev.redisinjava.command.transactions;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** UNWATCH: forgets watched keys. */
public final class UnwatchCommand extends BaseCommand
        implements dev.redisinjava.server.RedisServer.TxnControl {
    @Override
    public String name() {
        return "unwatch";
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        ctx.connection().unwatch();
        return RespValue.ok();
    }
}
