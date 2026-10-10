package dev.redisinjava;

import dev.redisinjava.command.CommandRegistry;
import dev.redisinjava.command.Commands;
import dev.redisinjava.persistence.AofLogger;
import dev.redisinjava.persistence.FlushPolicy;
import dev.redisinjava.pubsub.PubSub;
import dev.redisinjava.server.RedisServer;
import dev.redisinjava.server.ServerConfig;
import dev.redisinjava.store.KeySpace;
import dev.redisinjava.util.Log;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point: parses flags, wires the keyspace, pub/sub, AOF and command
 * registry into the event loop, replays the AOF, then serves until SIGTERM.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        ServerConfig config;
        try {
            config = ServerConfig.parseArgs(args);
        } catch (IllegalArgumentException bad) {
            System.err.println("Error: " + bad.getMessage());
            ServerConfig.printHelp();
            System.exit(1);
            return;
        }
        if (config == null) {
            return; // --help / --version already printed
        }

        KeySpace keySpace = new KeySpace();
        PubSub pubSub = new PubSub();
        FlushPolicy policy = switch (config.appendFsync()) {
            case "always" -> FlushPolicy.ALWAYS;
            case "no" -> FlushPolicy.NO;
            default -> FlushPolicy.EVERYSEC;
        };
        AofLogger aof = new AofLogger(Path.of(config.aofFile()), policy, config.appendOnly());
        CommandRegistry registry = Commands.all();
        RedisServer server = new RedisServer(config, keySpace, pubSub, aof, registry);

        if (config.appendOnly()) {
            AofLogger.replay(Path.of(config.aofFile()),
                    argv -> replayOne(server, registry, keySpace, pubSub, aof, config, argv));
            aof.baseNow();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Shutting down...");
            server.stop();
            try {
                aof.flush();
            } catch (Exception e) {
                Log.error("AOF flush on shutdown failed: " + e.getMessage());
            } finally {
                aof.close();
            }
            Log.info("Shutdown complete");
        }, "shutdown"));

        try {
            server.start();
        } catch (Exception e) {
            Log.error("Server failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Replays one AOF command without re-logging it. */
    static void replayOne(RedisServer server, CommandRegistry registry, KeySpace keySpace,
                          PubSub pubSub, AofLogger aof, ServerConfig config, List<String> argv) {
        if (argv.isEmpty()) {
            return;
        }
        var command = registry.lookup(argv.get(0).toLowerCase());
        if (command == null || !command.isWrite()) {
            return;
        }
        var ctx = new dev.redisinjava.command.CommandContext(
                keySpace, pubSub, aof, config, null, server, registry);
        command.execute(ctx, argv);
    }
}
