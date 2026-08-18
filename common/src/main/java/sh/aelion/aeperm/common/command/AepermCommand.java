package sh.aelion.aeperm.common.command;

import sh.aelion.aeperm.common.command.subcommand.CacheCommands;
import sh.aelion.aeperm.common.command.subcommand.CommandContext;
import sh.aelion.aeperm.common.command.subcommand.GroupCommands;
import sh.aelion.aeperm.common.command.subcommand.HistoryCommands;
import sh.aelion.aeperm.common.command.subcommand.InfoCommands;
import sh.aelion.aeperm.common.command.subcommand.SyncCommands;
import sh.aelion.aeperm.common.command.subcommand.UserCommands;

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
