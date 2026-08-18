package sh.aelion.aeperm.common.command.subcommand;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.aelion.aeperm.common.command.AepermSource;
import sh.aelion.aeperm.common.command.SubCommand;
import sh.aelion.aeperm.common.msg.Messages;

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
                    Component body = Component.text("AePerm by ", NamedTextColor.GRAY)
                            .append(Component.text(meta.author(), NamedTextColor.YELLOW))
                            .append(Component.text(", version ", NamedTextColor.GRAY))
                            .append(Component.text(meta.version(), NamedTextColor.YELLOW))
                            .append(Component.text(", ", NamedTextColor.GRAY))
                            .append(Component.text(meta.networkMode() ? "network" : "standalone", NamedTextColor.YELLOW))
                            .append(Component.text(".", NamedTextColor.GRAY));
                    c.getSource().audience().sendMessage(Messages.write(body));
                    return 1;
                })
                .build();
    }
}
