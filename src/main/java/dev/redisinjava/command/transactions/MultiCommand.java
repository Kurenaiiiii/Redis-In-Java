package dev.redisinjava.command.transactions;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** MULTI: starts queueing; nested MULTI is rejected. Never queued itself. */
public final class MultiCommand extends BaseCommand
        implements dev.redisinjava.server.RedisServer.TxnControl {
    @Override
    public String name() {
        return "multi";
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (ctx.connection().inMulti()) {
            return RespValue.err("ERR MULTI calls can not be nested");
        }
        ctx.connection().beginMulti();
        return RespValue.ok();
    }
}
