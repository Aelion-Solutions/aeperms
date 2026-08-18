package net.beteax.aeperm.common.command.subcommand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.beteax.aeperm.common.command.AepermSource;
import net.beteax.aeperm.common.command.Arguments;
import net.beteax.aeperm.common.command.SubCommand;
import net.beteax.aeperm.common.history.HistoryRecord;
import net.beteax.aeperm.common.msg.Messages;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class HistoryCommands extends SubCommand {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final net.beteax.aeperm.common.command.subcommand.CommandContext ctx;

    public HistoryCommands(net.beteax.aeperm.common.command.subcommand.CommandContext ctx) {
        super("history");
        this.ctx = ctx;
    }

    @Override
    public LiteralCommandNode<AepermSource> build() {
        return literal(name())
                .executes(c -> show(c, "", 1))
                .then(Arguments.integer("page").executes(c -> show(c, "", IntegerArgumentType.getInteger(c, "page"))))
                .then(literal("user")
                        .then(Arguments.word("target")
                                .executes(c -> show(c, StringArgumentType.getString(c, "target"), 1))
                                .then(Arguments.integer("page").executes(c ->
                                        show(c, StringArgumentType.getString(c, "target"), IntegerArgumentType.getInteger(c, "page"))))))
                .then(literal("group")
                        .then(Arguments.word("target")
                                .executes(c -> show(c, StringArgumentType.getString(c, "target"), 1))
                                .then(Arguments.integer("page").executes(c ->
                                        show(c, StringArgumentType.getString(c, "target"), IntegerArgumentType.getInteger(c, "page"))))))
                .build();
    }

    private int show(CommandContext<AepermSource> c, String filter, int page) {
        List<HistoryRecord> rows = ctx.permissions().history(filter, page);
        if (rows.isEmpty()) {
            return info(c.getSource(), "No history entries");
        }
        List<Messages.Line> lines = new ArrayList<>();
        for (HistoryRecord row : rows) {
            lines.add(new Messages.Line(
                    TIME.format(row.at()),
                    row.actor() + " " + row.action() + " " + row.target() + (row.detail().isBlank() ? "" : " " + row.detail())
            ));
        }
        Messages.card(c.getSource().audience(), "History p" + Math.max(page, 1), lines);
        return 1;
    }
}
