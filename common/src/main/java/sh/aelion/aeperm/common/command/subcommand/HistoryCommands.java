package sh.aelion.aeperm.common.command.subcommand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.aelion.aeperm.common.command.AepermSource;
import sh.aelion.aeperm.common.command.Arguments;
import sh.aelion.aeperm.common.command.SubCommand;
import sh.aelion.aeperm.common.history.HistoryRecord;
import sh.aelion.aeperm.common.msg.Messages;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class HistoryCommands extends SubCommand {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final sh.aelion.aeperm.common.command.subcommand.CommandContext ctx;

    public HistoryCommands(sh.aelion.aeperm.common.command.subcommand.CommandContext ctx) {
        super("history");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> show(c, "", "/ap history", 1))
                .then(Arguments.integer("page").executes(c ->
                        show(c, "", "/ap history", IntegerArgumentType.getInteger(c, "page"))))
                .then(literal("user")
                        .then(Arguments.word("target")
                                .executes(c -> {
                                    String target = StringArgumentType.getString(c, "target");
                                    return show(c, target, "/ap history user " + target, 1);
                                })
                                .then(Arguments.integer("page").executes(c -> {
                                    String target = StringArgumentType.getString(c, "target");
                                    return show(c, target, "/ap history user " + target,
                                            IntegerArgumentType.getInteger(c, "page"));
                                }))))
                .then(literal("group")
                        .then(Arguments.word("target")
                                .executes(c -> {
                                    String target = StringArgumentType.getString(c, "target");
                                    return show(c, target, "/ap history group " + target, 1);
                                })
                                .then(Arguments.integer("page").executes(c -> {
                                    String target = StringArgumentType.getString(c, "target");
                                    return show(c, target, "/ap history group " + target,
                                            IntegerArgumentType.getInteger(c, "page"));
                                }))))
                .build();
    }

    private int show(CommandContext<AepermSource> c, String filter, String commandBase, int page) {
        int total = ctx.permissions().storage().countHistory(filter);
        if (total == 0) {
            return info(c.getSource(), "No history entries");
        }
        int totalPages = Math.max(1, (total + Messages.PAGE_SIZE - 1) / Messages.PAGE_SIZE);
        int current = Math.clamp(page, 1, totalPages);
        int offset = (current - 1) * Messages.PAGE_SIZE;
        List<HistoryRecord> rows = ctx.permissions().storage().listHistory(filter, offset, Messages.PAGE_SIZE);

        List<Component> items = new ArrayList<>(rows.size());
        for (HistoryRecord row : rows) {
            String detail = row.detail() == null || row.detail().isBlank() ? "" : " " + row.detail();
            items.add(Component.text(TIME.format(row.at()) + " ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(row.actor(), NamedTextColor.YELLOW))
                    .append(Component.text(" " + row.action() + " ", NamedTextColor.GRAY))
                    .append(Component.text(row.target() + detail, NamedTextColor.GREEN)));
        }

        Messages.pagedSlice(
                c.getSource().audience(),
                "History",
                items,
                current,
                totalPages,
                commandBase);
        return 1;
    }
}
