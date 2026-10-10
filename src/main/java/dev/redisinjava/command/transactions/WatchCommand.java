package dev.redisinjava.command.transactions;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** WATCH key [key ...]: snapshots key versions for optimistic locking. */
public final class WatchCommand extends BaseCommand
        implements dev.redisinjava.server.RedisServer.TxnControl {
    @Override
    public String name() {
        return "watch";
    }

    @Override
    public int arity() {
        return -2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        Map<String, Long> versions = new HashMap<>();
        for (int i = 1; i < argv.size(); i++) {
            versions.put(argv.get(i), ctx.keySpace().version(argv.get(i)));
        }
        ctx.connection().watch(versions);
        return RespValue.ok();
    }
}
