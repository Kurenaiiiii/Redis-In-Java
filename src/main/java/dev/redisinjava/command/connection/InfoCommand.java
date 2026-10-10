package dev.redisinjava.command.connection;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/**
 * INFO [section]: basic server stats in the standard section format.
 * Unknown sections return an empty bulk string, like real Redis.
 */
public final class InfoCommand extends BaseCommand {
    @Override
    public String name() {
        return "info";
    }

    @Override
    public int arity() {
        return -1;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (argv.size() > 2) {
            return wrongArity(name());
        }
        String section = argv.size() == 2 ? argv.get(1).toLowerCase() : "default";
        Runtime runtime = Runtime.getRuntime();
        long uptime = (System.currentTimeMillis() - ctx.server().startedMillis()) / 1000;
        String server = "# Server\r\n"
                + "redis_version:0.1.0\r\n"
                + "redis_mode:standalone\r\n"
                + "arch_bits:64\r\n"
                + "uptime_in_seconds:" + uptime + "\r\n";
        String clients = "# Clients\r\n"
                + "connected_clients:" + ctx.server().connectionCount() + "\r\n"
                + "blocked_clients:0\r\n";
        String memory = "# Memory\r\n"
                + "used_memory:" + (runtime.totalMemory() - runtime.freeMemory()) + "\r\n"
                + "maxmemory:" + ctx.config().maxmemoryBytes() + "\r\n"
                + "maxmemory_policy:" + ctx.config().maxmemoryPolicy() + "\r\n";
        String persistence = "# Persistence\r\n"
                + "aof_enabled:" + (ctx.config().appendOnly() ? 1 : 0) + "\r\n"
                + "aof_rewrite_in_progress:0\r\n";
        String stats = "# Stats\r\n"
                + "total_commands_processed:" + ctx.server().totalCommands() + "\r\n";
        String replication = "# Replication\r\nrole:master\r\n";
        String keyspace = "# Keyspace\r\n"
                + "db0:keys=" + ctx.keySpace().size()
                + ",expires=" + ctx.keySpace().expires() + "\r\n";
        String all = switch (section) {
            case "default" -> server + clients + memory + persistence + stats + replication + keyspace;
            case "server" -> server;
            case "clients" -> clients;
            case "memory" -> memory;
            case "persistence" -> persistence;
            case "stats" -> stats;
            case "replication" -> replication;
            case "keyspace" -> keyspace;
            case "everything", "all" -> server + clients + memory + persistence + stats + replication + keyspace;
            default -> "";
        };
        return RespValue.bulk(Raw.encode(all));
    }
}
