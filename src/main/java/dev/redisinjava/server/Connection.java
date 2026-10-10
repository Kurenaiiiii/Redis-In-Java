package dev.redisinjava.server;

import dev.redisinjava.protocol.RespEncoder;
import dev.redisinjava.protocol.RespParser;
import dev.redisinjava.protocol.RespValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One client connection: the socket, the incremental RESP parser holding
 * partial reads, the queue of pending reply bytes, and per-connection state
 * (selected DB, transaction, subscriptions, idle tracking).
 */
public final class Connection {
    private final SocketChannel channel;
    private final RespParser parser = new RespParser();
    private final Deque<ByteBuffer> writeQueue = new ArrayDeque<>();

    private int db;
    private long lastActiveNanos = System.nanoTime();
    private boolean closeAfterDrain;

    private boolean inMulti;
    private final List<List<String>> txnQueue = new ArrayList<>();
    private Map<String, Long> watched;

    private final Set<String> channels = new HashSet<>();
    private final Set<String> patterns = new HashSet<>();

    public Connection(SocketChannel channel) {
        this.channel = channel;
    }

    public SocketChannel channel() {
        return channel;
    }

    public RespParser parser() {
        return parser;
    }

    public int db() {
        return db;
    }

    public void setDb(int db) {
        this.db = db;
    }

    public void touch() {
        lastActiveNanos = System.nanoTime();
    }

    public long idleSeconds() {
        return (System.nanoTime() - lastActiveNanos) / 1_000_000_000L;
    }

    /** Queues a reply; tries a direct write first for low latency. */
    public void send(RespValue reply) throws IOException {
        byte[] bytes = RespEncoder.encode(reply);
        if (writeQueue.isEmpty()) {
            int written = channel.write(ByteBuffer.wrap(bytes));
            if (written < bytes.length) {
                writeQueue.add(ByteBuffer.wrap(bytes, written, bytes.length - written));
            }
        } else {
            writeQueue.add(ByteBuffer.wrap(bytes));
        }
    }

    /** Flushes queued bytes; true when fully drained. */
    public boolean flush() throws IOException {
        while (!writeQueue.isEmpty()) {
            channel.write(writeQueue.peek());
            if (writeQueue.peek().hasRemaining()) {
                return false;
            }
            writeQueue.poll();
        }
        return true;
    }

    public boolean hasPendingWrites() {
        return !writeQueue.isEmpty();
    }

    public void closeAfterDrain() {
        closeAfterDrain = true;
    }

    public boolean closeWhenDrained() {
        return closeAfterDrain && writeQueue.isEmpty();
    }

    // --- transactions ---

    public boolean inMulti() {
        return inMulti;
    }

    public void beginMulti() {
        inMulti = true;
        txnQueue.clear();
    }

    public void enqueueTxn(List<String> argv) {
        txnQueue.add(new ArrayList<>(argv));
    }

    public List<List<String>> drainTxn() {
        List<List<String>> queued = new ArrayList<>(txnQueue);
        txnQueue.clear();
        inMulti = false;
        return queued;
    }

    public void abortTxn() {
        txnQueue.clear();
        inMulti = false;
    }

    public void watch(Map<String, Long> versions) {
        if (watched == null) {
            watched = new HashMap<>();
        }
        watched.putAll(versions);
    }

    public void unwatch() {
        watched = null;
    }

    public Map<String, Long> watched() {
        return watched;
    }

    // --- pub/sub ---

    public void subscribed(String channel) {
        channels.add(channel);
    }

    public void unsubscribed(String channel) {
        channels.remove(channel);
    }

    public void psubscribed(String pattern) {
        patterns.add(pattern);
    }

    public void punsubscribed(String pattern) {
        patterns.remove(pattern);
    }

    public boolean isSubscribed() {
        return !channels.isEmpty() || !patterns.isEmpty();
    }

    public Set<String> channels() {
        return channels;
    }

    public Set<String> patterns() {
        return patterns;
    }

    public int subscriptionCount() {
        return channels.size() + patterns.size();
    }
}
