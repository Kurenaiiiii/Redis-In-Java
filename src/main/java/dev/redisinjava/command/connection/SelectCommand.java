package dev.redisinjava.command.connection;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/**
 * SELECT index: only database 0 exists in this implementation; anything else
 * is rejected the way real Redis rejects out-of-range indexes.
 */
public final class SelectCommand extends BaseCommand {
    @Override
    public String name() {
        return "select";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        Long index = Raw.parseLong(argv.get(1));
        if (index == null) {
            return notInteger();
        }
        if (index != 0) {
            return RespValue.err("ERR DB index is out of range");
        }
        ctx.connection().setDb(0);
        return RespValue.ok();
    }
}
