package sh.aelion.aeperm.common.command.subcommand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.common.command.AepermSource;
import sh.aelion.aeperm.common.command.Arguments;
import sh.aelion.aeperm.common.command.SubCommand;
import sh.aelion.aeperm.common.msg.Messages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class UserCommands extends SubCommand {

    private final sh.aelion.aeperm.common.command.subcommand.CommandContext ctx;

    public UserCommands(sh.aelion.aeperm.common.command.subcommand.CommandContext ctx) {
        super("user");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> info(c.getSource(), "Usage: /ap user <player|uuid> <info|permission|group|check>"))
                .then(Arguments.player("target", ctx)
                        .executes(this::showInfo)
                        .then(literal("info").executes(this::showInfo))
                        .then(literal("permissions")
                                .executes(this::showPermissions)
                                .then(Arguments.integer("page").executes(this::showPermissions)))
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
            String target = user.name().orElse(uuid.toString());
            List<Component> lines = new ArrayList<>();
            lines.add(Messages.field("Name", user.name().orElse("-")));
            lines.add(Messages.field("UUID", uuid.toString()));
            lines.add(Messages.blank());
            String primary = user.primaryGroup().orElse("-");
            if ("-".equals(primary)) {
                lines.add(Messages.field("Primary", "-"));
            } else {
                lines.add(Messages.field("Primary", Messages.groupLink(primary)));
            }
            List<String> groups = new ArrayList<>(user.groups());
            groups.sort(String.CASE_INSENSITIVE_ORDER);
            groups.removeIf(g -> g.equalsIgnoreCase(primary));
            if (groups.isEmpty()) {
                lines.add(Messages.field("Group", "-"));
            } else {
                for (String group : groups) {
                    lines.add(Messages.field("Group", Messages.groupLink(group)));
                }
            }
            lines.add(Messages.blank());
            lines.add(Messages.item(Messages.permissionsLink(
                    user.permissions().size(),
                    "/ap user " + target + " permissions")));
            Messages.frame(audience, lines);
            return 1;
        });
    }

    private int showPermissions(CommandContext<AepermSource> c) {
        return withUser(c, (audience, uuid) -> {
            CalculatedUser user = ctx.permissions().user(uuid).orElseThrow();
            String target = user.name().orElse(uuid.toString());
            Messages.permissionPage(
                    audience,
                    "Permissions",
                    user.permissions(),
                    optionalPage(c),
                    "/ap user " + target + " permissions");
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

    private static int optionalPage(CommandContext<AepermSource> c) {
        try {
            return IntegerArgumentType.getInteger(c, "page");
        } catch (IllegalArgumentException ignored) {
            return 1;
        }
    }

    @FunctionalInterface
    private interface UserAction {
        int run(Audience audience, UUID uuid);
    }
}
