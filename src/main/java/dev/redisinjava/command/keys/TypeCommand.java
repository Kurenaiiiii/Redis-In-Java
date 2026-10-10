package dev.redisinjava.command.keys;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisValue;
import java.util.List;

/** TYPE key: the value's type name, or "none". */
public final class TypeCommand extends BaseCommand {
    @Override
    public String name() {
        return "type";
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RedisValue value = ctx.keySpace().get(argv.get(1));
        String type = value == null ? "none" : value.type().typeName();
        return new RespValue.SimpleString(type);
    }
}
