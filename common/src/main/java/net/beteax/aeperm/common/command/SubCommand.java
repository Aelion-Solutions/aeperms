package net.beteax.aeperm.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.beteax.aeperm.common.msg.Messages;

public abstract class SubCommand {

    private final String name;

    protected SubCommand(String name) {
        this.name = name;
    }

    public final String name() {
        return name;
    }

    public abstract LiteralCommandNode<AepermSource> build();

    protected static LiteralArgumentBuilder<AepermSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    protected static int info(AepermSource source, String message) {
        Messages.info(source.audience(), message);
        return 1;
    }

    protected static int error(AepermSource source, String message) {
        Messages.error(source.audience(), message);
        return 0;
    }

    protected static int success(AepermSource source, String message) {
        Messages.success(source.audience(), message);
        return 1;
    }
}
