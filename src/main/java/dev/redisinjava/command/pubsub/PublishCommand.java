package dev.redisinjava.command.pubsub;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.List;

/** PUBLISH channel message: delivers to subscribers, returns receiver count. */
public final class PublishCommand extends PubSubBase {
    @Override
    public String name() {
        return "publish";
    }

    @Override
    public int arity() {
        return 3;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        int receivers = ctx.pubSub().publish(argv.get(1), argv.get(2), ctx.server());
        return RespValue.intVal(receivers);
    }
}
