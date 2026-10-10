package dev.redisinjava.command.pubsub;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.ArrayList;
import java.util.List;

/** PUNSUBSCRIBE [pattern ...]: default removes every pattern. */
public final class PunsubscribeCommand extends PubSubBase {
    @Override
    public String name() {
        return "punsubscribe";
    }

    @Override
    public int arity() {
        return -1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        List<String> targets = argv.size() == 1
                ? new ArrayList<>(ctx.connection().patterns())
                : argv.subList(1, argv.size());
        List<RespValue> replies = new ArrayList<>();
        for (String pattern : targets) {
            ctx.pubSub().punsubscribe(ctx.connection(), pattern);
            ctx.connection().punsubscribed(pattern);
            replies.add(subscriptionMessage(
                    "punsubscribe", pattern, ctx.connection().subscriptionCount()));
        }
        return sendAll(ctx.connection(), replies);
    }
}
