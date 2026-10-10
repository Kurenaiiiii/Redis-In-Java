package dev.redisinjava.command;

import dev.redisinjava.command.connection.EchoCommand;
import dev.redisinjava.command.connection.PingCommand;
import dev.redisinjava.command.connection.QuitCommand;

/**
 * Registers every command. Add one line here per new command class; the
 * registry (not a switch) is the single place commands become visible.
 */
public final class Commands {
    private Commands() {
    }

    public static CommandRegistry all() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new PingCommand());
        registry.register(new EchoCommand());
        registry.register(new QuitCommand());
        return registry;
    }
}
