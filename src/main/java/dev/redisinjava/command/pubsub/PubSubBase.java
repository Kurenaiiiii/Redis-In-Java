package dev.redisinjava.command.pubsub;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.server.Connection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared pub/sub reply plumbing. Subscribe-family commands emit one message
 * per channel, so they send directly and return null (the dispatcher then
 * sends nothing further).
 */
public abstract class PubSubBase extends BaseCommand {
    protected static RespValue sendAll(Connection conn, List<RespValue> messages) {
        try {
            for (int i = 0; i < messages.size(); i++) {
                conn.send(messages.get(i));
            }
        } catch (IOException e) {
            return RespValue.err("ERR internal error");
        }
        return null;
    }

    protected static RespValue subscriptionMessage(String kind, String target, int count) {
        List<RespValue> items = new ArrayList<>(3);
        items.add(bulk(kind));
        items.add(bulk(target));
        items.add(RespValue.intVal(count));
        return RespValue.array(items);
    }
}
