package dev.redisinjava.command.pubsub;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.ArrayList;
import java.util.List;

/** SUBSCRIBE channel [channel ...]: subscribes, one confirm per channel. */
public final class SubscribeCommand extends PubSubBase {
    @Override
    public String name() {
        return "subscribe";
    }

    @Override
    public int arity() {
        return -2;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        List<RespValue> replies = new ArrayList<>();
        for (int i = 1; i < argv.size(); i++) {
            ctx.pubSub().subscribe(ctx.connection(), argv.get(i));
            ctx.connection().subscribed(argv.get(i));
            replies.add(subscriptionMessage(
                    "subscribe", argv.get(i), ctx.connection().subscriptionCount()));
        }
        return sendAll(ctx.connection(), replies);
    }
}
