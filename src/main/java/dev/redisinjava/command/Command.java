package dev.redisinjava.command;

import dev.redisinjava.protocol.RespValue;
import java.util.List;

/**
 * One Redis command. {@code argv} includes the command name at index 0,
 * exactly as it arrived on the wire, so handlers can re-log it for the AOF.
 */
public interface Command {
    /** Lowercase command name, e.g. {@code "get"}. */
    String name();

    /**
     * Redis-style arity: positive means exactly that many arguments
     * (including the name), negative means at least that many.
     */
    int arity();

    /** True for commands that mutate the dataset (logged to the AOF). */
    default boolean isWrite() {
        return false;
    }

    RespValue execute(CommandContext ctx, List<String> argv);
}
