package dev.redisinjava;

import dev.redisinjava.command.CommandRegistry;
import dev.redisinjava.command.Commands;
import dev.redisinjava.persistence.AofLogger;
import dev.redisinjava.persistence.FlushPolicy;
import dev.redisinjava.pubsub.PubSub;
import dev.redisinjava.server.RedisServer;
import dev.redisinjava.server.ServerConfig;
import dev.redisinjava.store.KeySpace;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Boots a real server on an ephemeral port and talks to it over a plain
 * socket with hand-rolled RESP framing, so tests exercise the full path:
 * NIO read, parse, dispatch, encode, NIO write.
 */
public final class ServerHarness implements AutoCloseable {
    private final RedisServer server;
    private final Thread thread;
    private final int port;

    public ServerHarness() throws IOException {
        this(new ServerConfig());
    }

    public ServerHarness(ServerConfig config) throws IOException {
        config.setPort(0); // ephemeral port, read back via server.port()
        KeySpace keySpace = new KeySpace();
        PubSub pubSub = new PubSub();
        AofLogger aof = new AofLogger(
                java.nio.file.Path.of("target/test-" + System.nanoTime() + ".aof"),
                FlushPolicy.NO, false);
        server = new RedisServer(config, keySpace, pubSub, aof, Commands.all());
        thread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "test-server");
        thread.setDaemon(true);
        thread.start();
        long deadline = System.currentTimeMillis() + 10000;
        while (server.port() == 0) {
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("Test server did not start");
            }
            Thread.yield();
        }
        port = server.port();
    }

    public int port() {
        return port;
    }

    public TestClient client() throws IOException {
        return new TestClient(port);
    }

    @Override
    public void close() throws IOException {
        server.stop();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Minimal blocking RESP client for tests. */
    public static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        TestClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(10000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
        }

        /** Sends argv and returns the reply: String, Long, List, null, or RedisError. */
        public Object cmd(String... argv) throws IOException {
            StringBuilder head = new StringBuilder("*").append(argv.length).append("\r\n");
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            for (String arg : argv) {
                byte[] raw = arg.getBytes(StandardCharsets.ISO_8859_1);
                out.write(("$" + raw.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(raw);
                out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            out.flush();
            return readReply();
        }

        /** Reads one server-pushed message (pub/sub delivery). */
        public Object read() throws IOException {
            return readReply();
        }

        private int readByte() throws IOException {
            int next = in.read();
            if (next < 0) {
                throw new IOException("Connection closed by server");
            }
            return next;
        }

        private String readLine() throws IOException {
            StringBuilder line = new StringBuilder();
            while (true) {
                int next = readByte();
                if (next == '\r') {
                    int lf = readByte();
                    if (lf != '\n') {
                        throw new IOException("Bad line ending");
                    }
                    return line.toString();
                }
                line.append((char) next);
            }
        }

        private Object readReply() throws IOException {
            int type = readByte();
            return switch (type) {
                case '+' -> readLine();
                case '-' -> new RedisError(readLine());
                case ':' -> Long.parseLong(readLine());
                case '$' -> {
                    int len = Integer.parseInt(readLine());
                    if (len == -1) {
                        yield null;
                    }
                    byte[] data = in.readNBytes(len);
                    readLine();
                    yield new String(data, StandardCharsets.ISO_8859_1);
                }
                case '*' -> {
                    int count = Integer.parseInt(readLine());
                    if (count == -1) {
                        yield null;
                    }
                    List<Object> items = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        items.add(readReply());
                    }
                    yield items;
                }
                default -> throw new IOException("Unknown reply type: " + (char) type);
            };
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** An error reply from the server. */
    public record RedisError(String message) {
    }
}
