package sh.aelion.aeperm.common.command.subcommand;

import com.mojang.brigadier.tree.LiteralCommandNode;
import sh.aelion.aeperm.common.command.AepermSource;
import sh.aelion.aeperm.common.command.SubCommand;
import sh.aelion.aeperm.common.msg.Messages;

import java.util.List;

public final class CacheCommands extends SubCommand {

    private final CommandContext ctx;

    public CacheCommands(CommandContext ctx) {
        super("cache");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> info(c.getSource(), "Usage: /ap cache <clear|stats>"))
                .then(literal("clear").executes(c -> {
                    ctx.permissions().reloadAll();
                    return success(c.getSource(), "Cache cleared");
                }))
                .then(literal("stats").executes(c -> {
                    Messages.card(c.getSource().audience(), "Cache Stats", List.of(
                            new Messages.Line("Users", String.valueOf(ctx.permissions().cache().userCount())),
                            new Messages.Line("Groups", String.valueOf(ctx.permissions().cache().groupCount()))
                    ));
                    return 1;
                }))
                .build();
    }
}
