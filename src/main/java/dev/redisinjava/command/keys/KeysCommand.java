package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.ArrayList;
import java.util.List;

/** KEYS pattern: live keys matching a glob. */
public final class KeysCommand extends BaseCommand {
    @Override
    public String name() {
        return "keys";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        List<RespValue> out = new ArrayList<>();
        for (String key : ctx.keySpace().keys(argv.get(1))) {
            out.add(bulk(key));
        }
        return RespValue.array(out);
    }
}
