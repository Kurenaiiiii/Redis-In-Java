package dev.redisinjava.server;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.Command;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.command.CommandRegistry;
import dev.redisinjava.persistence.AofLogger;
import dev.redisinjava.protocol.ProtocolException;
import dev.redisinjava.protocol.RespEncoder;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.pubsub.PubSub;
import dev.redisinjava.store.KeySpace;
import dev.redisinjava.util.Log;
import dev.redisinjava.util.Raw;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Single-threaded non-blocking event loop, the same architecture as real
 * Redis: one {@link Selector} accepts connections and pumps request bytes
 * into per-connection buffers; complete commands run to completion inline,
 * so no locking is needed anywhere.
 *
 * <p>The loop also fires periodic chores: active expiry (100ms), AOF
 * everysec fsync, idle-timeout sweeps and AOF auto-rewrite checks (1s).
 */
public final class RedisServer implements PubSub.PubSubMessenger {
    private static final Set<String> SUBSCRIBED_ALLOWED =
            Set.of("subscribe", "unsubscribe", "psubscribe", "punsubscribe", "ping", "quit");

    private final ServerConfig config;
    private final KeySpace keySpace;
    private final PubSub pubSub;
    private final AofLogger aof;
    private final CommandRegistry registry;

    private Selector selector;
    private ServerSocketChannel listener;
    private final Set<Connection> connections = new HashSet<>();
    private volatile boolean running;
    private long startedMillis;
    private long totalCommands;
    private int boundPort;

    public RedisServer(ServerConfig config, KeySpace keySpace, PubSub pubSub,
                       AofLogger aof, CommandRegistry registry) {
        this.config = config;
        this.keySpace = keySpace;
        this.pubSub = pubSub;
        this.aof = aof;
        this.registry = registry;
    }

    public int port() {
        return boundPort;
    }

    public long startedMillis() {
        return startedMillis;
    }

    public long totalCommands() {
        return totalCommands;
    }

    public int connectionCount() {
        return connections.size();
    }

    public void start() throws IOException {
        selector = Selector.open();
        listener = ServerSocketChannel.open();
        listener.configureBlocking(false);
        listener.bind(new InetSocketAddress(config.port()));
        boundPort = ((InetSocketAddress) listener.getLocalAddress()).getPort();
        listener.register(selector, SelectionKey.OP_ACCEPT);
        running = true;
        startedMillis = System.currentTimeMillis();
        Log.info("Listening on port " + boundPort);

        long lastExpireCheck = 0;
        long lastSecondCheck = 0;
        ByteBuffer readBuf = ByteBuffer.allocate(64 * 1024);

        while (running) {
            selector.select(100);
            long now = System.currentTimeMillis();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if (!key.isValid()) {
                    continue;
                }
                try {
                    if (key.isAcceptable()) {
                        accept();
                    } else {
                        Connection conn = (Connection) key.attachment();
                        if (key.isReadable()) {
                            read(conn, readBuf);
                        }
                        if (key.isValid() && key.isWritable()) {
                            write(conn);
                        }
                    }
                } catch (IOException e) {
                    closeQuietly(key);
                }
            }
            if (now - lastExpireCheck >= 100) {
                lastExpireCheck = now;
                for (int i = 0; i < 3 && keySpace.sweepOnce() > 0; i++) {
                    // keep sweeping while a full sample expired
                }
            }
            if (now - lastSecondCheck >= 1000) {
                lastSecondCheck = now;
                secondly();
            }
        }
        closeAll();
    }

    /** Wakes the loop so it exits; the shutdown hook calls this on SIGTERM. */
    public void stop() {
        running = false;
        if (selector != null) {
            selector.wakeup();
        }
    }

    private void accept() throws IOException {
        SocketChannel channel = listener.accept();
        if (channel == null) {
            return;
        }
        if (connections.size() >= config.maxclients()) {
            channel.close();
            Log.warn("Connection refused: maxclients limit reached");
            return;
        }
        channel.configureBlocking(false);
        channel.socket().setTcpNoDelay(true);
        Connection conn = new Connection(channel);
        connections.add(conn);
        channel.register(selector, SelectionKey.OP_READ, conn);
    }

    private void read(Connection conn, ByteBuffer readBuf) throws IOException {
        readBuf.clear();
        int bytes = conn.channel().read(readBuf);
        if (bytes == -1) {
            close(conn);
            return;
        }
        if (bytes == 0) {
            return;
        }
        conn.touch();
        conn.parser().feed(readBuf.array(), 0, bytes);
        List<RespValue> values;
        try {
            values = conn.parser().drain();
        } catch (ProtocolException bad) {
            conn.send(RespValue.err(bad.getMessage()));
            conn.flush();
            close(conn);
            return;
        }
        for (RespValue value : values) {
            dispatch(conn, value);
            if (!connections.contains(conn)) {
                break; // closed mid-pipeline (protocol error)
            }
            if (conn.closeWhenDrained() && !conn.hasPendingWrites()) {
                close(conn);
                break;
            }
        }
        if (connections.contains(conn) && conn.hasPendingWrites()) {
            interest(conn, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            write(conn);
        }
    }

    private void write(Connection conn) throws IOException {
        if (conn.flush() && !conn.hasPendingWrites()) {
            if (conn.closeWhenDrained()) {
                close(conn);
            } else {
                interest(conn, SelectionKey.OP_READ);
            }
        }
    }

    private void interest(Connection conn, int ops) {
        SelectionKey key = conn.channel().keyFor(selector);
        if (key != null && key.isValid()) {
            key.interestOps(ops);
        }
    }

    void dispatch(Connection conn, RespValue value) throws IOException {
        List<String> argv = toArgv(value);
        if (argv == null) {
            conn.send(RespValue.err("ERR Protocol error: command must be an array of bulk strings"));
            close(conn);
            return;
        }
        if (argv.isEmpty()) {
            return;
        }
        String name = argv.get(0).toLowerCase();
        totalCommands++;

        if (conn.isSubscribed() && !SUBSCRIBED_ALLOWED.contains(name)) {
            conn.send(RespValue.err("ERR Can't execute '" + name
                    + "': only (P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT are allowed in this context"));
            return;
        }
        Command command = registry.lookup(name);
        if (conn.inMulti() && !(command instanceof TxnControl)) {
            // Queue-time errors dirty the transaction: EXEC then fails.
            if (command == null) {
                conn.send(unknownCommand(argv));
                conn.markTxnDirty();
                return;
            }
            if (name.equals("watch") || name.equals("unwatch")) {
                conn.send(RespValue.err("ERR WATCH inside MULTI is not allowed"));
                conn.markTxnDirty();
                return;
            }
            if (!checkArity(conn, command, argv)) {
                conn.markTxnDirty();
                return;
            }
            conn.enqueueTxn(argv);
            conn.send(RespValue.queued());
            return;
        }
        if (command == null) {
            conn.send(unknownCommand(argv));
            return;
        }
        if (!checkArity(conn, command, argv)) {
            return;
        }
        if (command.isWrite() && !checkMemory(conn, command)) {
            return;
        }
        CommandContext ctx = new CommandContext(
                keySpace, pubSub, aof, config, conn, this, registry);
        RespValue reply;
        try {
            reply = command.execute(ctx, argv);
        } catch (Exception e) {
            Log.error("Command '" + name + "' failed: " + e);
            reply = RespValue.err("ERR internal error");
        }
        if (reply == null) {
            return; // command already wrote its replies (multi-message commands)
        }
        if (command.isWrite() && !(reply instanceof RespValue.Error)) {
            aof.log(argv);
        }
        conn.send(reply);
        if (conn.closeWhenDrained() && !conn.hasPendingWrites()) {
            close(conn);
        }
    }

    private boolean checkArity(Connection conn, Command command, List<String> argv)
            throws IOException {
        int arity = command.arity();
        boolean ok = arity > 0 ? argv.size() == arity : argv.size() >= -arity;
        if (!ok) {
            conn.send(RespValue.err(
                    "ERR wrong number of arguments for '" + command.name() + "' command"));
        }
        return ok;
    }

    /**
     * Pre-write memory check. {@code noeviction} fails fast with OOM;
     * {@code allkeys-lru} samples and evicts before running the command.
     */
    private boolean checkMemory(Connection conn, Command command) throws IOException {
        if (config.maxmemoryBytes() <= 0
                || keySpace.usedMemoryBytes() <= config.maxmemoryBytes()) {
            return true;
        }
        if (config.maxmemoryPolicy().equals("allkeys-lru")) {
            evict();
            return true;
        }
        conn.send(RespValue.err("OOM command not allowed when used memory > 'maxmemory'."));
        return false;
    }

    private void evict() {
        for (int i = 0; i < 10
                && keySpace.usedMemoryBytes() > config.maxmemoryBytes(); i++) {
            List<String> sample = keySpace.sample(5);
            if (sample.isEmpty()) {
                break;
            }
            String oldest = null;
            long oldestAccess = Long.MAX_VALUE;
            for (String key : sample) {
                long access = keySpace.lastAccessNanos(key);
                if (access < oldestAccess) {
                    oldestAccess = access;
                    oldest = key;
                }
            }
            if (oldest == null || !keySpace.delete(oldest)) {
                break;
            }
        }
    }

    private void secondly() {
        aof.everySecond();
        if (config.appendOnly() && aof.shouldRewrite(
                config.autoRewritePercentage(), config.autoRewriteMinSize())) {
            try {
                aof.rewrite(keySpace);
            } catch (IOException e) {
                Log.error("Automatic AOF rewrite failed: " + e.getMessage());
            }
        }
        if (config.timeoutSeconds() > 0) {
            for (Connection conn : new ArrayList<>(connections)) {
                if (!conn.isSubscribed() && conn.idleSeconds() >= config.timeoutSeconds()) {
                    closeQuietly(conn);
                }
            }
        }
    }

    private void close(Connection conn) {
        connections.remove(conn);
        pubSub.removeAll(conn);
        try {
            conn.channel().close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }

    private void closeQuietly(Connection conn) {
        SelectionKey key = conn.channel().keyFor(selector);
        if (key != null) {
            key.cancel();
        }
        close(conn);
    }

    private void closeQuietly(SelectionKey key) {
        try {
            Object attach = key.attachment();
            if (attach instanceof Connection conn) {
                close(conn);
            } else {
                key.channel().close();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        key.cancel();
    }

    private void closeAll() {
        for (Connection conn : new ArrayList<>(connections)) {
            close(conn);
        }
        try {
            if (listener != null) {
                listener.close();
            }
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            Log.warn("Error during shutdown: " + e.getMessage());
        }
    }

    private static List<String> toArgv(RespValue value) {
        if (!(value instanceof RespValue.Array array) || array.items() == null) {
            return null;
        }
        List<String> argv = new ArrayList<>(array.items().size());
        for (RespValue item : array.items()) {
            if (!(item instanceof RespValue.BulkString bulk) || bulk.value() == null) {
                return null;
            }
            argv.add(Raw.decode(bulk.value()));
        }
        return argv;
    }

    private static RespValue unknownCommand(List<String> argv) {
        StringBuilder message = new StringBuilder("unknown command `")
                .append(argv.get(0)).append('`');
        if (argv.size() > 1) {
            message.append(", with args starting with:");
            for (int i = 1; i < Math.min(argv.size(), 4); i++) {
                message.append(" `").append(argv.get(i)).append('`');
            }
        }
        return RespValue.err("ERR " + message);
    }

    @Override
    public void message(Connection conn, String channel, String payload) {
        deliver(conn, RespValue.array(
                BaseCommand.bulk("message"), BaseCommand.bulk(channel), BaseCommand.bulk(payload)));
    }

    @Override
    public void patternMessage(Connection conn, String pattern, String channel, String payload) {
        deliver(conn, RespValue.array(BaseCommand.bulk("pmessage"),
                BaseCommand.bulk(pattern), BaseCommand.bulk(channel), BaseCommand.bulk(payload)));
    }

    private void deliver(Connection conn, RespValue value) {
        if (!connections.contains(conn)) {
            return;
        }
        try {
            conn.send(value);
            interest(conn, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            conn.flush();
            if (!conn.hasPendingWrites()) {
                interest(conn, SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            close(conn);
        }
    }

    /** Marker for MULTI/EXEC/DISCARD/WATCH: never queued, always run inline. */
    public interface TxnControl {
    }

    /** Encodes a reply for tests without a socket. */
    public static byte[] encodeReply(RespValue reply) {
        return RespEncoder.encode(reply);
    }
}
