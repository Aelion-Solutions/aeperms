package sh.aelion.aeperm.common.command.subcommand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.aelion.aeperm.api.CalculatedGroup;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.common.command.AepermSource;
import sh.aelion.aeperm.common.command.Arguments;
import sh.aelion.aeperm.common.command.SubCommand;
import sh.aelion.aeperm.common.msg.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GroupCommands extends SubCommand {

    private final sh.aelion.aeperm.common.command.subcommand.CommandContext ctx;

    public GroupCommands(sh.aelion.aeperm.common.command.subcommand.CommandContext ctx) {
        super("group");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> info(c.getSource(), "Usage: /ap group <list|create|delete|name> ..."))
                .then(literal("list").executes(this::listGroups))
                .then(literal("create")
                        .then(Arguments.word("name").executes(this::createGroup)))
                .then(literal("delete")
                        .then(Arguments.group("name", ctx).executes(this::deleteGroup)))
                .then(Arguments.group("name", ctx)
                        .executes(this::showInfo)
                        .then(literal("info").executes(this::showInfo))
                        .then(literal("permissions")
                                .executes(this::showPermissions)
                                .then(Arguments.integer("page").executes(this::showPermissions)))
                        .then(literal("permission")
                                .executes(c -> info(c.getSource(), "Usage: /ap group <g> permission <set|unset> <node>"))
                                .then(literal("set")
                                        .then(Arguments.word("node").executes(this::setPermission)))
                                .then(literal("unset")
                                        .then(Arguments.word("node").executes(this::unsetPermission))))
                        .then(literal("parent")
                                .executes(c -> info(c.getSource(), "Usage: /ap group <g> parent <add|remove> <parent>"))
                                .then(literal("add")
                                        .then(Arguments.group("parent", ctx).executes(this::addParent)))
                                .then(literal("remove")
                                        .then(Arguments.group("parent", ctx).executes(this::removeParent))))
                        .then(literal("weight")
                                .then(Arguments.integer("weight").executes(this::setWeight))))
                .build();
    }

    private int listGroups(CommandContext<AepermSource> c) {
        Audience sender = c.getSource().audience();
        List<String> names = new ArrayList<>(ctx.permissions().groupNames());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.isEmpty()) {
            return info(c.getSource(), "No groups found");
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Messages.item(Component.text("Groups (" + names.size() + ")", NamedTextColor.YELLOW)));
        lines.add(Messages.blank());
        for (String name : names) {
            Optional<CalculatedGroup> group = ctx.permissions().group(name);
            if (group.isEmpty()) {
                continue;
            }
            CalculatedGroup g = group.get();
            String parents = g.parents().isEmpty() ? "-" : String.join(", ", g.parents());
            lines.add(Messages.item(
                    Messages.groupLink(g.name())
                            .append(Component.text(
                                    "  weight=" + g.weight() + " parents=" + parents + " nodes=" + g.permissions().size(),
                                    NamedTextColor.GRAY))));
        }
        Messages.frame(sender, lines);
        return 1;
    }

    private int createGroup(CommandContext<AepermSource> c) {
        String name = StringArgumentType.getString(c, "name");
        ctx.permissions().createGroup(name);
        return success(c.getSource(), "Created group <yellow>" + name + "</yellow>");
    }

    private int deleteGroup(CommandContext<AepermSource> c) {
        String name = StringArgumentType.getString(c, "name");
        try {
            ctx.permissions().deleteGroup(name);
            return success(c.getSource(), "Deleted group <yellow>" + name + "</yellow>");
        } catch (IllegalArgumentException ex) {
            return error(c.getSource(), ex.getMessage());
        }
    }

    private int showInfo(CommandContext<AepermSource> c) {
        String group = StringArgumentType.getString(c, "name");
        Audience sender = c.getSource().audience();
        Optional<CalculatedGroup> found = ctx.permissions().group(group);
        if (found.isEmpty()) {
            Messages.error(sender, "Unknown group");
            return 1;
        }
        CalculatedGroup g = found.get();
        List<Component> lines = new ArrayList<>();
        lines.add(Messages.field("Name", g.name()));
        lines.add(Messages.field("Weight", String.valueOf(g.weight())));
        lines.add(Messages.blank());
        List<String> parents = new ArrayList<>(g.parents());
        parents.sort(String.CASE_INSENSITIVE_ORDER);
        if (parents.isEmpty()) {
            lines.add(Messages.field("Parent", "-"));
        } else {
            for (String parent : parents) {
                lines.add(Messages.field("Parent", Messages.groupLink(parent)));
            }
        }
        lines.add(Messages.blank());
        lines.add(Messages.item(Messages.permissionsLink(
                g.permissions().size(),
                "/ap group " + g.name() + " permissions")));
        Messages.frame(sender, lines);
        return 1;
    }

    private int showPermissions(CommandContext<AepermSource> c) {
        String group = StringArgumentType.getString(c, "name");
        Audience sender = c.getSource().audience();
        Optional<CalculatedGroup> found = ctx.permissions().group(group);
        if (found.isEmpty()) {
            Messages.error(sender, "Unknown group");
            return 1;
        }
        CalculatedGroup g = found.get();
        Messages.permissionPage(
                sender,
                "Permissions",
                g.permissions(),
                optionalPage(c),
                "/ap group " + g.name() + " permissions");
        return 1;
    }

    private int setPermission(CommandContext<AepermSource> c) {
        String group = StringArgumentType.getString(c, "name");
        String node = StringArgumentType.getString(c, "node");
        ctx.permissions().groupAdd(group, node, ContextSet.empty(), null);
        return success(c.getSource(), "Group permission set");
    }

    private int unsetPermission(CommandContext<AepermSource> c) {
        String group = StringArgumentType.getString(c, "name");
        String node = StringArgumentType.getString(c, "node");
        ctx.permissions().groupRemove(group, node, ContextSet.empty());
        return success(c.getSource(), "Group permission unset");
    }

    private int addParent(CommandContext<AepermSource> c) {
        return updateParent(c, true);
    }

    private int removeParent(CommandContext<AepermSource> c) {
        return updateParent(c, false);
    }

    private int updateParent(CommandContext<AepermSource> c, boolean add) {
        String group = StringArgumentType.getString(c, "name");
        String parent = StringArgumentType.getString(c, "parent");
        try {
            if (add) {
                ctx.permissions().addParent(group, parent);
            } else {
                ctx.permissions().removeParent(group, parent);
            }
            return success(c.getSource(), "Parent updated");
        } catch (IllegalArgumentException ex) {
            return error(c.getSource(), ex.getMessage());
        }
    }

    private int setWeight(CommandContext<AepermSource> c) {
        String group = StringArgumentType.getString(c, "name");
        int weight = IntegerArgumentType.getInteger(c, "weight");
        ctx.permissions().setGroupWeight(group, weight);
        return success(c.getSource(), "Weight updated");
    }

    private static int optionalPage(CommandContext<AepermSource> c) {
        try {
            return IntegerArgumentType.getInteger(c, "page");
        } catch (IllegalArgumentException ignored) {
            return 1;
        }
    }
}
