package dev.redisinjava.command;

import dev.redisinjava.command.connection.CommandCmd;
import dev.redisinjava.command.connection.EchoCommand;
import dev.redisinjava.command.connection.InfoCommand;
import dev.redisinjava.command.connection.PingCommand;
import dev.redisinjava.command.connection.QuitCommand;
import dev.redisinjava.command.connection.SelectCommand;
import dev.redisinjava.command.hashes.HdelCommand;
import dev.redisinjava.command.hashes.HexistsCommand;
import dev.redisinjava.command.hashes.HgetCommand;
import dev.redisinjava.command.hashes.HgetallCommand;
import dev.redisinjava.command.hashes.HsetCommand;
import dev.redisinjava.command.keys.DbsizeCommand;
import dev.redisinjava.command.keys.ExpireCommand;
import dev.redisinjava.command.keys.FlushallCommand;
import dev.redisinjava.command.keys.KeysCommand;
import dev.redisinjava.command.keys.PersistCommand;
import dev.redisinjava.command.keys.PexpireCommand;
import dev.redisinjava.command.keys.PttlCommand;
import dev.redisinjava.command.keys.TtlCommand;
import dev.redisinjava.command.keys.TypeCommand;
import dev.redisinjava.command.lists.LlenCommand;
import dev.redisinjava.command.lists.LpopCommand;
import dev.redisinjava.command.lists.LpushCommand;
import dev.redisinjava.command.lists.LrangeCommand;
import dev.redisinjava.command.lists.RpopCommand;
import dev.redisinjava.command.lists.RpushCommand;
import dev.redisinjava.command.sets.SaddCommand;
import dev.redisinjava.command.sets.ScardCommand;
import dev.redisinjava.command.sets.SismemberCommand;
import dev.redisinjava.command.sets.SmembersCommand;
import dev.redisinjava.command.sets.SremCommand;
import dev.redisinjava.command.zsets.ZaddCommand;
import dev.redisinjava.command.zsets.ZcardCommand;
import dev.redisinjava.command.zsets.ZrangeCommand;
import dev.redisinjava.command.zsets.ZrangebyscoreCommand;
import dev.redisinjava.command.zsets.ZrankCommand;
import dev.redisinjava.command.zsets.ZremCommand;
import dev.redisinjava.command.zsets.ZscoreCommand;
import dev.redisinjava.command.strings.AppendCommand;
import dev.redisinjava.command.strings.DecrCommand;
import dev.redisinjava.command.strings.DelCommand;
import dev.redisinjava.command.strings.ExistsCommand;
import dev.redisinjava.command.strings.GetCommand;
import dev.redisinjava.command.strings.IncrByCommand;
import dev.redisinjava.command.strings.IncrCommand;
import dev.redisinjava.command.strings.MgetCommand;
import dev.redisinjava.command.strings.MsetCommand;
import dev.redisinjava.command.strings.SetCommand;
import dev.redisinjava.command.strings.StrlenCommand;

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
        registry.register(new CommandCmd());
        registry.register(new InfoCommand());
        registry.register(new SelectCommand());
        registry.register(new SetCommand());
        registry.register(new GetCommand());
        registry.register(new DelCommand());
        registry.register(new ExistsCommand());
        registry.register(new IncrCommand());
        registry.register(new DecrCommand());
        registry.register(new IncrByCommand());
        registry.register(new AppendCommand());
        registry.register(new StrlenCommand());
        registry.register(new MsetCommand());
        registry.register(new MgetCommand());
        registry.register(new ExpireCommand());
        registry.register(new PexpireCommand());
        registry.register(new TtlCommand());
        registry.register(new PttlCommand());
        registry.register(new PersistCommand());
        registry.register(new TypeCommand());
        registry.register(new KeysCommand());
        registry.register(new FlushallCommand());
        registry.register(new DbsizeCommand());
        registry.register(new LpushCommand());
        registry.register(new RpushCommand());
        registry.register(new LpopCommand());
        registry.register(new RpopCommand());
        registry.register(new LlenCommand());
        registry.register(new LrangeCommand());
        registry.register(new HsetCommand());
        registry.register(new HgetCommand());
        registry.register(new HdelCommand());
        registry.register(new HgetallCommand());
        registry.register(new HexistsCommand());
        registry.register(new SaddCommand());
        registry.register(new SremCommand());
        registry.register(new SmembersCommand());
        registry.register(new SismemberCommand());
        registry.register(new ScardCommand());
        registry.register(new ZaddCommand());
        registry.register(new ZremCommand());
        registry.register(new ZscoreCommand());
        registry.register(new ZcardCommand());
        registry.register(new ZrankCommand());
        registry.register(new ZrangeCommand());
        registry.register(new ZrangebyscoreCommand());
        return registry;
    }
}
