package net.beteax.aeperm.common.command;

import net.beteax.aeperm.common.command.subcommand.CacheCommands;
import net.beteax.aeperm.common.command.subcommand.CommandContext;
import net.beteax.aeperm.common.command.subcommand.GroupCommands;
import net.beteax.aeperm.common.command.subcommand.HistoryCommands;
import net.beteax.aeperm.common.command.subcommand.InfoCommands;
import net.beteax.aeperm.common.command.subcommand.SyncCommands;
import net.beteax.aeperm.common.command.subcommand.UserCommands;

import java.util.List;

public final class AepermCommand extends Command {

    public AepermCommand(CommandContext ctx) {
        super("aeperm", List.of("ap"), "aeperm.admin");
        add(new UserCommands(ctx));
        add(new GroupCommands(ctx));
        add(new CacheCommands(ctx));
        add(new SyncCommands(ctx));
        add(new HistoryCommands(ctx));
        add(new InfoCommands(ctx));
    }
}
