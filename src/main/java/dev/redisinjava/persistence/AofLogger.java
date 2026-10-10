package dev.redisinjava.persistence;

import dev.redisinjava.protocol.RespEncoder;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.store.KeySpace;
import dev.redisinjava.store.RedisHashValue;
import dev.redisinjava.store.RedisListValue;
import dev.redisinjava.store.RedisSetValue;
import dev.redisinjava.store.RedisStringValue;
import dev.redisinjava.store.RedisValue;
import dev.redisinjava.store.RedisZSetValue;
import dev.redisinjava.store.SkipList;
import dev.redisinjava.util.Log;
import dev.redisinjava.util.Raw;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Append-Only File log. Every mutating command is appended in RESP format so
 * replaying the file rebuilds the dataset. fsync behavior follows the
 * {@code appendfsync} policy; see {@link FlushPolicy}.
 */
public final class AofLogger {
    private final Path path;
    private FlushPolicy policy;
    private boolean enabled;
    private OutputStream out;
    private long bytesSinceBase;
    private long baseSize;

    public AofLogger(Path path, FlushPolicy policy, boolean enabled) {
        this.path = path;
        this.policy = policy;
        this.enabled = enabled;
    }

    public synchronized void open() throws IOException {
        if (out == null) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            out = new BufferedOutputStream(Files.newOutputStream(path,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        }
    }

    public synchronized void setEnabled(boolean enabled) throws IOException {
        this.enabled = enabled;
        if (enabled) {
            open();
        } else {
            flush();
        }
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setPolicy(FlushPolicy policy) throws IOException {
        this.policy = policy;
        flush();
    }

    public synchronized FlushPolicy policy() {
        return policy;
    }

    /** Appends one command; caller passes the original argv strings. */
    public synchronized void log(List<String> argv) {
        if (!enabled) {
            return;
        }
        try {
            open();
            List<RespValue> items = new ArrayList<>(argv.size());
            for (String arg : argv) {
                items.add(RespValue.bulk(Raw.encode(arg)));
            }
            byte[] encoded = RespEncoder.encode(RespValue.array(items));
            out.write(encoded);
            bytesSinceBase += encoded.length;
            // everysec/no hand data to the OS on every command and only differ
            // in fsync cadence; always fsyncs inline for full durability.
            out.flush();
            if (policy == FlushPolicy.ALWAYS) {
                fsync();
            }
        } catch (IOException e) {
            Log.error("AOF write failed: " + e.getMessage());
        }
    }

    /** Flushes buffered data; fsyncs unless the policy is {@code no}. */
    public synchronized void flush() throws IOException {
        if (out != null) {
            out.flush();
            if (policy != FlushPolicy.NO) {
                fsync();
            }
        }
    }

    /** Called once per second by the event loop for {@code everysec}. */
    public synchronized void everySecond() {
        if (!enabled || out == null || policy != FlushPolicy.EVERYSEC) {
            return;
        }
        try {
            out.flush();
            fsync();
        } catch (IOException e) {
            Log.error("AOF fsync failed: " + e.getMessage());
        }
    }

    public synchronized long fileSize() {
        try {
            return Files.exists(path) ? Files.size(path) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    public synchronized long bytesSinceBase() {
        return bytesSinceBase;
    }

    public synchronized void baseNow() {
        bytesSinceBase = 0;
        baseSize = fileSize();
    }

    public synchronized long baseSize() {
        return baseSize;
    }

    /** True when growth since the base exceeds the configured thresholds. */
    public synchronized boolean shouldRewrite(int percentage, long minSize) {
        long size = fileSize();
        if (size < minSize) {
            return false;
        }
        if (baseSize == 0) {
            return size > 0;
        }
        return (size - baseSize) * 100L / baseSize >= percentage;
    }

    /**
     * Rewrites the log to the minimal command sequence recreating the live
     * dataset. Runs synchronously on the event-loop thread (real Redis forks
     * instead); the file is replaced atomically via rename, and writes
     * cannot interleave because the loop is blocked meanwhile.
     */
    public synchronized void rewrite(KeySpace keySpace) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        long keys = 0;
        try (OutputStream tmpOut = new BufferedOutputStream(Files.newOutputStream(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (String key : keySpace.keys("*")) {
                RedisValue value = keySpace.peek(key);
                if (value == null) {
                    continue;
                }
                writeCommand(tmpOut, minimalCommands(key, value));
                long expireAt = keySpace.expireAt(key);
                if (expireAt >= 0) {
                    writeCommand(tmpOut,
                            List.of(List.of("PEXPIREAT", key, Long.toString(expireAt))));
                }
                keys++;
            }
            tmpOut.flush();
        }
        if (out != null) {
            out.flush();
            out.close();
            out = null;
        }
        Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (enabled) {
            open();
        }
        baseNow();
        Log.info("AOF rewrite compacted " + keys + " keys");
    }

    /** Minimal write commands recreating one key. */
    private static List<List<String>> minimalCommands(String key, RedisValue value) {
        return switch (value.type()) {
            case STRING -> List.of(List.of("SET", key,
                    Raw.decode(((RedisStringValue) value).data())));
            case LIST -> {
                List<String> cmd = new ArrayList<>(List.of("RPUSH", key));
                for (byte[] item : ((RedisListValue) value).items()) {
                    cmd.add(Raw.decode(item));
                }
                yield List.of(cmd);
            }
            case HASH -> {
                List<String> cmd = new ArrayList<>(List.of("HSET", key));
                for (Map.Entry<String, byte[]> field
                        : ((RedisHashValue) value).fields().entrySet()) {
                    cmd.add(field.getKey());
                    cmd.add(Raw.decode(field.getValue()));
                }
                yield List.of(cmd);
            }
            case SET -> {
                List<String> cmd = new ArrayList<>(List.of("SADD", key));
                cmd.addAll(((RedisSetValue) value).members());
                yield List.of(cmd);
            }
            case ZSET -> {
                List<String> cmd = new ArrayList<>(List.of("ZADD", key));
                RedisZSetValue zset = (RedisZSetValue) value;
                for (SkipList.Entry entry
                        : zset.skiplist().rangeByRank(0, -1)) {
                    cmd.add(Double.toString(entry.score()));
                    cmd.add(entry.member());
                }
                yield List.of(cmd);
            }
            default -> List.of();
        };
    }

    private static void writeCommand(OutputStream tmpOut, List<List<String>> commands)
            throws IOException {
        for (List<String> argv : commands) {
            List<RespValue> items = new ArrayList<>(argv.size());
            for (String arg : argv) {
                items.add(RespValue.bulk(Raw.encode(arg)));
            }
            tmpOut.write(RespEncoder.encode(RespValue.array(items)));
        }
    }

    public synchronized void close() {
        if (out != null) {
            try {
                out.flush();
                fsync();
                out.close();
            } catch (IOException e) {
                Log.error("AOF close failed: " + e.getMessage());
            } finally {
                out = null;
            }
        }
    }

    private void fsync() throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    /**
     * Replays the log, feeding each command to {@code executor}. A torn tail
     * (crash mid-write) stops the replay with a warning, like Redis does.
     */
    public static void replay(Path path, Consumer<List<String>> executor) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            byte[] data = Files.readAllBytes(path);
            dev.redisinjava.protocol.RespParser parser = new dev.redisinjava.protocol.RespParser();
            parser.feed(data, 0, data.length);
            List<RespValue> values;
            try {
                values = parser.drain();
            } catch (dev.redisinjava.protocol.ProtocolException torn) {
                Log.warn("AOF has a torn tail, replaying what parsed cleanly");
                values = List.of();
            }
            int applied = 0;
            for (RespValue value : values) {
                if (!(value instanceof RespValue.Array array) || array.items() == null) {
                    continue;
                }
                List<String> argv = new ArrayList<>(array.items().size());
                boolean ok = true;
                for (RespValue item : array.items()) {
                    if (item instanceof RespValue.BulkString bulk && bulk.value() != null) {
                        argv.add(Raw.decode(bulk.value()));
                    } else {
                        ok = false;
                        break;
                    }
                }
                if (ok && !argv.isEmpty()) {
                    try {
                        executor.accept(argv);
                        applied++;
                    } catch (Exception e) {
                        Log.warn("AOF replay skipped command: " + e.getMessage());
                    }
                }
            }
            if (!parser.isEmpty()) {
                Log.warn("AOF has trailing garbage bytes, ignored");
            }
            Log.info("AOF replay applied " + applied + " commands from " + path);
        } catch (IOException e) {
            Log.error("AOF replay failed: " + e.getMessage());
        }
    }
}
