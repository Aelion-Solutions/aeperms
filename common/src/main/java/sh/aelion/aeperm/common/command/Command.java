package sh.aelion.aeperm.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import sh.aelion.aeperm.common.msg.Messages;

import java.util.ArrayList;
import java.util.List;

public abstract class Command {

    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final List<SubCommand> subcommands = new ArrayList<>();

    protected Command(String name, List<String> aliases, String permission) {
        this.name = name;
        this.aliases = List.copyOf(aliases);
        this.permission = permission;
    }

    protected final void add(SubCommand subcommand) {
        subcommands.add(subcommand);
    }

    public final String name() {
        return name;
    }

    public final List<String> aliases() {
        return aliases;
    }

    public final String permission() {
        return permission;
    }

    public final LiteralCommandNode<AepermSource> build() {
        LiteralArgumentBuilder<AepermSource> root = LiteralArgumentBuilder.<AepermSource>literal(name)
                .requires(source -> source.hasPermission(permission))
                .executes(ctx -> {
                    usage(ctx.getSource());
                    return 1;
                });
        for (SubCommand subcommand : subcommands) {
            root.then(subcommand.build());
        }
        return root.build();
    }

    protected void usage(AepermSource source) {
        Messages.info(source.audience(), "Usage: /ap <user|group|cache|sync|history|info>");
    }
}
