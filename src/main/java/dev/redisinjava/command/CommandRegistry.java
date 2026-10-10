package dev.redisinjava.command;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Maps command names to handlers. Commands self-register here instead of a
 * giant switch, so adding one means adding one class plus one line.
 */
public final class CommandRegistry {
    private final Map<String, Command> commands = new TreeMap<>();

    public void register(Command command) {
        commands.put(command.name(), command);
    }

    public Command lookup(String name) {
        return commands.get(name.toLowerCase());
    }

    public Collection<Command> all() {
        return commands.values();
    }
}
