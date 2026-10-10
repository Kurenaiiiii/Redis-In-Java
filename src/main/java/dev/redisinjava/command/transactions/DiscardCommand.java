package dev.redisinjava.command.transactions;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** DISCARD: drops the queue and forgets watched keys. */
public final class DiscardCommand extends BaseCommand
        implements dev.redisinjava.server.RedisServer.TxnControl {
    @Override
    public String name() {
        return "discard";
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (!ctx.connection().inMulti()) {
            return RespValue.err("ERR DISCARD without MULTI");
        }
        ctx.connection().abortTxn();
        ctx.connection().clearTxnDirty();
        ctx.connection().unwatch();
        return RespValue.ok();
    }
}
