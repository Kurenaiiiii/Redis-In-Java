package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.RedisStringValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.ValueType;
import dev.redisinjava.util.Raw;
import java.util.Arrays;
import java.util.List;

/** APPEND key value: appends bytes, creating the key when missing. */
public final class AppendCommand extends BaseCommand {
    @Override
    public String name() {
        return "append";
    }

    @Override
    public int arity() {
        return 3;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        RespValue[] error = new RespValue[1];
        RedisValue current = typed(ctx, argv.get(1), ValueType.STRING, error);
        if (error[0] != null) {
            return error[0];
        }
        byte[] tail = Raw.encode(argv.get(2));
        if (current == null) {
            ctx.keySpace().put(argv.get(1), new RedisStringValue(tail));
            return RespValue.intVal(tail.length);
        }
        byte[] head = ((RedisStringValue) current).data();
        byte[] joined = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, joined, head.length, tail.length);
        // put() preserves the TTL and fixes memory accounting.
        ctx.keySpace().put(argv.get(1), new RedisStringValue(joined));
        return RespValue.intVal(joined.length);
    }
}
