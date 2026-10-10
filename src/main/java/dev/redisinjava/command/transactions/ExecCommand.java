package dev.redisinjava.command.transactions;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.Command;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EXEC: runs the queue atomically (nothing interleaves on one thread).
 * Returns nil when a watched key moved, EXECABORT when queueing saw errors;
 * per-command errors are collected as elements, like real Redis.
 */
public final class ExecCommand extends BaseCommand
        implements dev.redisinjava.server.RedisServer.TxnControl {
    @Override
    public String name() {
        return "exec";
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (!ctx.connection().inMulti()) {
            return RespValue.err("ERR EXEC without MULTI");
        }
        List<List<String>> queued = ctx.connection().drainTxn();
        if (ctx.connection().isTxnDirty()) {
            ctx.connection().clearTxnDirty();
            ctx.connection().unwatch();
            return RespValue.err(
                    "EXECABORT Transaction discarded because of previous errors.");
        }
        Map<String, Long> watched = ctx.connection().watched();
        if (watched != null) {
            for (Map.Entry<String, Long> entry : watched.entrySet()) {
                if (ctx.keySpace().version(entry.getKey()) != entry.getValue()) {
                    ctx.connection().unwatch();
                    return RespValue.nilArray();
                }
            }
            ctx.connection().unwatch();
        }
        List<RespValue> replies = new ArrayList<>(queued.size());
        for (List<String> command : queued) {
            Command handler = ctx.registry().lookup(command.get(0).toLowerCase());
            RespValue reply;
            try {
                reply = handler.execute(ctx, command);
            } catch (Exception e) {
                reply = RespValue.err("ERR internal error");
            }
            if (handler.isWrite() && !(reply instanceof RespValue.Error)) {
                ctx.aof().log(command);
            }
            // Multi-message commands (subscribe family) send directly; EXEC
            // stands in a plain OK for them.
            replies.add(reply == null ? RespValue.ok() : reply);
        }
        return RespValue.array(replies);
    }
}
