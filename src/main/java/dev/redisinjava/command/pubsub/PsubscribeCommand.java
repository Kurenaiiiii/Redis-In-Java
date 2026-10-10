package dev.redisinjava.command.pubsub;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.ArrayList;
import java.util.List;

/** PSUBSCRIBE pattern [pattern ...]: glob subscriptions. */
public final class PsubscribeCommand extends PubSubBase {
    @Override
    public String name() {
        return "psubscribe";
    }

    @Override
    public int arity() {
        return -2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        List<RespValue> replies = new ArrayList<>();
        for (int i = 1; i < argv.size(); i++) {
            ctx.pubSub().psubscribe(ctx.connection(), argv.get(i));
            ctx.connection().psubscribed(argv.get(i));
            replies.add(subscriptionMessage(
                    "psubscribe", argv.get(i), ctx.connection().subscriptionCount()));
        }
        return sendAll(ctx.connection(), replies);
    }
}
