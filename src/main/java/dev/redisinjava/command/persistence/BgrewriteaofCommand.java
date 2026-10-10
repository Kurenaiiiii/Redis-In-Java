package dev.redisinjava.command.persistence;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Log;
import java.util.List;

/**
 * BGREWRITEAOF: compacts the log to the minimal recreating sequence.
 * Real Redis rewrites in a child process; this runs synchronously on the
 * event loop, so latency scales with dataset size (documented trade-off).
 */
public final class BgrewriteaofCommand extends BaseCommand {
    @Override
    public String name() {
        return "bgrewriteaof";
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        try {
            ctx.aof().rewrite(ctx.keySpace());
            return new RespValue.SimpleString(
                    "Background append only file rewriting started");
        } catch (Exception e) {
            Log.error("AOF rewrite failed: " + e.getMessage());
            return RespValue.err("ERR AOF rewrite failed: " + e.getMessage());
        }
    }
}
