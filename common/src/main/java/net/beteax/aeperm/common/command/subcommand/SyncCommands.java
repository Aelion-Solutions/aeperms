package net.beteax.aeperm.common.command.subcommand;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.beteax.aeperm.common.command.AepermSource;
import net.beteax.aeperm.common.command.SubCommand;
import net.beteax.aeperm.common.msg.Messages;

import java.util.List;

public final class SyncCommands extends SubCommand {

    private final CommandContext ctx;

    public SyncCommands(CommandContext ctx) {
        super("sync");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> info(c.getSource(), "Usage: /ap sync <reload|status>"))
                .then(literal("reload").executes(c -> {
                    ctx.permissions().reloadNetwork();
                    return success(c.getSource(), "Sync reload complete");
                }))
                .then(literal("status").executes(c -> {
                    Messages.card(c.getSource().audience(), "Sync Status", List.of(
                            new Messages.Line("Mode", ctx.meta().networkMode() ? "network" : "standalone"),
                            new Messages.Line("Groups", String.valueOf(ctx.permissions().groupNames().size())),
                            new Messages.Line("Cached users", String.valueOf(ctx.permissions().cache().userCount()))
                    ));
                    return 1;
                }))
                .build();
    }
}
