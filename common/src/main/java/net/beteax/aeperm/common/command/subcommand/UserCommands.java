package net.beteax.aeperm.common.command.subcommand;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.beteax.aeperm.api.CalculatedUser;
import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.common.command.AepermSource;
import net.beteax.aeperm.common.command.Arguments;
import net.beteax.aeperm.common.command.SubCommand;
import net.beteax.aeperm.common.msg.Messages;
import net.kyori.adventure.audience.Audience;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class UserCommands extends SubCommand {

    private final net.beteax.aeperm.common.command.subcommand.CommandContext ctx;

    public UserCommands(net.beteax.aeperm.common.command.subcommand.CommandContext ctx) {
        super("user");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> info(c.getSource(), "Usage: /ap user <player|uuid> <info|permission|group|check>"))
                .then(Arguments.player("target", ctx)
                        .executes(c -> info(c.getSource(), "Usage: /ap user <player|uuid> <info|permission|group|check>"))
                        .then(literal("info").executes(this::showInfo))
                        .then(literal("check")
                                .then(Arguments.word("node").executes(this::check)))
                        .then(literal("permission")
                                .executes(c -> info(c.getSource(), "Usage: /ap user <p> permission <set|unset> <node> [seconds]"))
                                .then(literal("set")
                                        .then(Arguments.word("node")
                                                .executes(this::setPermission)
                                                .then(Arguments.seconds("seconds").executes(this::setPermission))))
                                .then(literal("unset")
                                        .then(Arguments.word("node").executes(this::unsetPermission))))
                        .then(literal("group")
                                .executes(c -> info(c.getSource(), "Usage: /ap user <p> group <add|remove|primary> <group> [seconds]"))
                                .then(literal("add")
                                        .then(Arguments.group("group", ctx)
                                                .executes(this::addGroup)
                                                .then(Arguments.seconds("seconds").executes(this::addGroup))))
                                .then(literal("remove")
                                        .then(Arguments.group("group", ctx).executes(this::removeGroup)))
                                .then(literal("primary")
                                        .then(Arguments.group("group", ctx).executes(this::setPrimary)))))
                .build();
    }

    private int showInfo(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            CalculatedUser user = ctx.permissions().user(uuid).orElseThrow();
            Messages.card(audience, "User Info", List.of(
                    new Messages.Line("UUID", uuid.toString()),
                    new Messages.Line("Name", user.name().orElse("-")),
                    new Messages.Line("Primary", user.primaryGroup().orElse("-")),
                    new Messages.Line("Groups", user.groups().isEmpty() ? "-" : String.join(", ", user.groups())),
                    new Messages.Line("Nodes", String.valueOf(user.permissions().size()))
            ));
            return 1;
        });
    }

    private int check(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            String node = StringArgumentType.getString(c, "node");
            boolean result = ctx.permissions().has(uuid, node);
            Messages.info(audience, "Check <yellow>" + node + "</yellow> = <yellow>" + result + "</yellow>");
            return 1;
        });
    }

    private int setPermission(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            String node = StringArgumentType.getString(c, "node");
            Duration ttl = optionalSeconds(c);
            ctx.permissions().userAdd(uuid, node, ContextSet.empty(), ttl);
            Messages.success(audience, "Set <yellow>" + node + "</yellow> for user");
            return 1;
        });
    }

    private int unsetPermission(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            String node = StringArgumentType.getString(c, "node");
            ctx.permissions().userRemove(uuid, node, ContextSet.empty());
            Messages.success(audience, "Unset <yellow>" + node + "</yellow> for user");
            return 1;
        });
    }

    private int addGroup(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            String group = StringArgumentType.getString(c, "group");
            ctx.permissions().addToGroup(uuid, group, optionalSeconds(c));
            Messages.success(audience, "Added user to <yellow>" + group + "</yellow>");
            return 1;
        });
    }

    private int removeGroup(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            String group = StringArgumentType.getString(c, "group");
            ctx.permissions().removeFromGroup(uuid, group);
            Messages.success(audience, "Removed user from <yellow>" + group + "</yellow>");
            return 1;
        });
    }

    private int setPrimary(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            String group = StringArgumentType.getString(c, "group");
            ctx.permissions().setPrimaryGroup(uuid, group);
            Messages.success(audience, "Primary group set to <yellow>" + group + "</yellow>");
            return 1;
        });
    }

    private int withUser(CommandContext<AepermSource> c, UserAction action) {
        String raw = StringArgumentType.getString(c, "target");
        Optional<UUID> uuid = ctx.resolveTarget(raw);
        if (uuid.isEmpty()) {
            return error(c.getSource(), "Unknown player <yellow>" + raw + "</yellow>");
        }
        return action.run(c.getSource().audience(), uuid.get());
    }

    private static Duration optionalSeconds(CommandContext<AepermSource> c) {
        try {
            return Duration.ofSeconds(LongArgumentType.getLong(c, "seconds"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface UserAction {
        int run(Audience audience, UUID uuid);
    }
}
