package dev.redisinjava.command.connection;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.persistence.FlushPolicy;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CONFIG GET pattern / CONFIG SET name value for the supported settings.
 * Persistence settings are applied to the live AOF logger, not just stored.
 */
public final class ConfigCommand extends BaseCommand {
    @Override
    public String name() {
        return "config";
    }

    @Override
    public int arity() {
        return -3;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        if (argv.size() < 3) {
            return wrongArity(name());
        }
        String sub = argv.get(1).toLowerCase();
        if (sub.equals("get")) {
            if (argv.size() != 3) {
                return wrongArity(name());
            }
            List<RespValue> out = new ArrayList<>();
            for (Map.Entry<String, String> entry
                    : ctx.config().get(argv.get(2)).entrySet()) {
                out.add(bulk(entry.getKey()));
                out.add(bulk(entry.getValue()));
            }
            return RespValue.array(out);
        }
        if (sub.equals("set")) {
            if (argv.size() != 4) {
                return wrongArity(name());
            }
            return set(ctx, argv.get(2), argv.get(3));
        }
        return RespValue.err("ERR unknown subcommand '" + argv.get(1)
                + "'. Try CONFIG GET, CONFIG SET.");
    }

    private RespValue set(CommandContext ctx, String name, String value) {
        // Capture persistence state before, to sync the live logger after.
        boolean wasAppendOnly = ctx.config().appendOnly();
        String error = ctx.config().set(name, value);
        if (error != null) {
            return RespValue.err(error);
        }
        try {
            if (name.equalsIgnoreCase("appendonly")) {
                ctx.aof().setEnabled(ctx.config().appendOnly());
            } else if (name.equalsIgnoreCase("appendfsync")) {
                ctx.aof().setPolicy(switch (ctx.config().appendFsync()) {
                    case "always" -> FlushPolicy.ALWAYS;
                    case "no" -> FlushPolicy.NO;
                    default -> FlushPolicy.EVERYSEC;
                });
            }
        } catch (Exception e) {
            Log.error("CONFIG SET " + name + " failed: " + e.getMessage());
            return RespValue.err("ERR CONFIG SET failed: " + e.getMessage());
        }
        if (wasAppendOnly != ctx.config().appendOnly()) {
            Log.info("appendonly " + (ctx.config().appendOnly() ? "enabled" : "disabled"));
        }
        return RespValue.ok();
    }
}
