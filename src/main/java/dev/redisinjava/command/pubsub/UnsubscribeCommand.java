package dev.redisinjava.command.pubsub;

import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import java.util.ArrayList;
import java.util.List;

/** UNSUBSCRIBE [channel ...]: default unsubscribes from every channel. */
public final class UnsubscribeCommand extends PubSubBase {
    @Override
    public String name() {
        return "unsubscribe";
    }

    @Override
    public int arity() {
        return -1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        List<String> targets = argv.size() == 1
                ? new ArrayList<>(ctx.connection().channels())
                : argv.subList(1, argv.size());
        List<RespValue> replies = new ArrayList<>();
        for (String channel : targets) {
            ctx.pubSub().unsubscribe(ctx.connection(), channel);
            ctx.connection().unsubscribed(channel);
            replies.add(subscriptionMessage(
                    "unsubscribe", channel, ctx.connection().subscriptionCount()));
        }
        return sendAll(ctx.connection(), replies);
    }
}
