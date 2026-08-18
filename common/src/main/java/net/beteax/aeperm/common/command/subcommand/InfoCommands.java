package net.beteax.aeperm.common.command.subcommand;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.beteax.aeperm.common.command.AepermSource;
import net.beteax.aeperm.common.command.SubCommand;
import net.beteax.aeperm.common.msg.Messages;

import java.util.List;

public final class InfoCommands extends SubCommand {

    private final CommandContext ctx;

    public InfoCommands(CommandContext ctx) {
        super("info");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> {
                    CommandMeta meta = ctx.meta();
                    Messages.card(c.getSource().audience(), "Plugin Info", List.of(
                            new Messages.Line("Name", "AePerm"),
                            new Messages.Line("Version", meta.version()),
                            new Messages.Line("Author", meta.author()),
                            new Messages.Line("Website", meta.website()),
                            new Messages.Line("Mode", meta.networkMode() ? "network" : "standalone")
                    ));
                    return 1;
                })
                .build();
    }
}
