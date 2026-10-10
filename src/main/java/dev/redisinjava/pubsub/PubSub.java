package dev.redisinjava.pubsub;

import dev.redisinjava.server.Connection;
import dev.redisinjava.util.Glob;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Channel and pattern subscriptions. Delivery appends the message straight
 * into each subscriber's write buffer on the event-loop thread, so no
 * cross-thread queue is needed.
 */
public final class PubSub {
    private final Map<String, Set<Connection>> channels = new HashMap<>();
    private final Map<Connection, Set<String>> patterns = new HashMap<>();

    public void subscribe(Connection conn, String channel) {
        channels.computeIfAbsent(channel, k -> new HashSet<>()).add(conn);
    }

    public boolean unsubscribe(Connection conn, String channel) {
        Set<Connection> subs = channels.get(channel);
        if (subs == null || !subs.remove(conn)) {
            return false;
        }
        if (subs.isEmpty()) {
            channels.remove(channel);
        }
        return true;
    }

    public void psubscribe(Connection conn, String pattern) {
        patterns.computeIfAbsent(conn, k -> new HashSet<>()).add(pattern);
    }

    public boolean punsubscribe(Connection conn, String pattern) {
        Set<String> pats = patterns.get(conn);
        if (pats == null || !pats.remove(pattern)) {
            return false;
        }
        if (pats.isEmpty()) {
            patterns.remove(conn);
        }
        return true;
    }

    /** Drops every subscription held by a closing connection. */
    public void removeAll(Connection conn) {
        for (Set<Connection> subs : channels.values()) {
            subs.remove(conn);
        }
        channels.entrySet().removeIf(e -> e.getValue().isEmpty());
        patterns.remove(conn);
    }

    /**
     * Publishes to channel subscribers and glob-matching pattern subscribers.
     * Returns the number of receiving connections.
     */
    public int publish(String channel, String message, PubSubMessenger messenger) {
        int receivers = 0;
        Set<Connection> direct = channels.get(channel);
        if (direct != null) {
            for (Connection conn : direct) {
                messenger.message(conn, channel, message);
                receivers++;
            }
        }
        for (Map.Entry<Connection, Set<String>> entry : patterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (Glob.match(pattern, channel)) {
                    messenger.patternMessage(entry.getKey(), pattern, channel, message);
                    receivers++;
                    break;
                }
            }
        }
        return receivers;
    }

    /** Callback the server implements to format and enqueue deliveries. */
    public interface PubSubMessenger {
        void message(Connection conn, String channel, String payload);

        void patternMessage(Connection conn, String pattern, String channel, String payload);
    }
}
