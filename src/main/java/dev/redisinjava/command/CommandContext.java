package dev.redisinjava.command;

import dev.redisinjava.persistence.AofLogger;
import dev.redisinjava.pubsub.PubSub;
import dev.redisinjava.server.Connection;
import dev.redisinjava.server.RedisServer;
import dev.redisinjava.server.ServerConfig;
import dev.redisinjava.store.KeySpace;

/**
 * Everything a command may touch: the dataset, config, pub/sub, the AOF,
 * the registry (for {@code EXEC}) and the server (for time/clients).
 */
public final class CommandContext {
    private final KeySpace keySpace;
    private final PubSub pubSub;
    private final AofLogger aof;
    private final ServerConfig config;
    private final Connection connection;
    private final RedisServer server;
    private final CommandRegistry registry;

    public CommandContext(KeySpace keySpace, PubSub pubSub, AofLogger aof,
                          ServerConfig config, Connection connection,
                          RedisServer server, CommandRegistry registry) {
        this.keySpace = keySpace;
        this.pubSub = pubSub;
        this.aof = aof;
        this.config = config;
        this.connection = connection;
        this.server = server;
        this.registry = registry;
    }

    public KeySpace keySpace() {
        return keySpace;
    }

    public PubSub pubSub() {
        return pubSub;
    }

    public AofLogger aof() {
        return aof;
    }

    public ServerConfig config() {
        return config;
    }

    public Connection connection() {
        return connection;
    }

    public RedisServer server() {
        return server;
    }

    public CommandRegistry registry() {
        return registry;
    }
}
