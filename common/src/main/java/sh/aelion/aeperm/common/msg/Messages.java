package sh.aelion.aeperm.common.msg;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Messages {

    public static final int PAGE_SIZE = 8;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<color:#a200ff>AePerm</color> <gray>»</gray> ";
    private static final Component ARROW = MM.deserialize("<gray> » </gray>");
    private static final Component BAR = MM.deserialize(
            "<dark_gray><strikethrough>--------------------------------</strikethrough></dark_gray>");

    private Messages() {
    }

    public static Component info(String body) {
        return MM.deserialize(PREFIX + "<gray>" + body + "</gray>");
    }

    public static Component write(Component body) {
        return MM.deserialize(PREFIX).append(body);
    }

    public static Component error(String body) {
        return MM.deserialize(PREFIX + "<red>" + body + "</red>");
    }

    public static Component success(String body) {
        return MM.deserialize(PREFIX + "<green>" + body + "</green>");
    }

    public static Component separator() {
        return ARROW.append(BAR);
    }

    public static Component blank() {
        return ARROW;
    }

    public static Component item(Component body) {
        return ARROW.append(body);
    }

    public static Component field(String label, String value) {
        return field(label, Component.text(value, NamedTextColor.GREEN));
    }

    public static Component field(String label, Component value) {
        return ARROW
                .append(Component.text(label + ": ", NamedTextColor.GRAY))
                .append(value);
    }

    public static Component clickRun(String label, String command, String hover) {
        return clickRun(Component.text(label, NamedTextColor.GOLD), command, hover);
    }

    public static Component clickRun(Component label, String command, String hover) {
        return label
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    public static Component groupLink(String name) {
        return clickRun(Component.text(name, NamedTextColor.GOLD), "/ap group " + name, "View group " + name);
    }

    public static Component permissionsLink(int count, String command) {
        String text = count + (count == 1 ? " permission" : " permissions");
        return clickRun(Component.text(text, NamedTextColor.GREEN), command, "Click to list permissions");
    }

    public static Component cardHeader(String title) {
        return item(Component.text(title, NamedTextColor.YELLOW));
    }

    public static Component cardLine(String label, String value) {
        return field(label, value);
    }

    public static void info(Audience audience, String body) {
        audience.sendMessage(info(body));
    }

    public static void error(Audience audience, String body) {
        audience.sendMessage(error(body));
    }

    public static void success(Audience audience, String body) {
        audience.sendMessage(success(body));
    }

    public static void frame(Audience audience, List<Component> lines) {
        audience.sendMessage(separator());
        for (Component line : lines) {
            audience.sendMessage(line);
        }
        audience.sendMessage(separator());
    }

    public static void card(Audience audience, String title, List<Line> lines) {
        List<Component> out = new ArrayList<>();
        out.add(cardHeader(title));
        out.add(blank());
        for (Line line : lines) {
            out.add(field(line.label(), line.value()));
        }
        frame(audience, out);
    }

    public static void permissionPage(
            Audience audience,
            String header,
            Map<String, Boolean> permissions,
            int page,
            String commandBase
    ) {
        if (permissions == null || permissions.isEmpty()) {
            info(audience, "No permissions");
            return;
        }
        List<String> keys = new ArrayList<>(permissions.keySet());
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        int totalPages = Math.max(1, (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.clamp(page, 1, totalPages);
        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, keys.size());

        List<Component> lines = new ArrayList<>();
        lines.add(item(Component.text(header, NamedTextColor.YELLOW)));
        lines.add(blank());
        for (int i = from; i < to; i++) {
            String node = keys.get(i);
            boolean allowed = Boolean.TRUE.equals(permissions.get(node));
            Component mark = Component.text(allowed ? "+ " : "- ", allowed ? NamedTextColor.GREEN : NamedTextColor.RED);
            lines.add(item(mark.append(Component.text(node, NamedTextColor.GRAY))));
        }
        lines.add(blank());
        lines.add(item(pageFooter(current, totalPages, commandBase)));
        frame(audience, lines);
    }

    public static String prefixRaw() {
        return PREFIX;
    }

    private static Component pageFooter(int current, int total, String commandBase) {
        Component prev = current > 1
                ? clickRun(Component.text("«", NamedTextColor.YELLOW), commandBase + " " + (current - 1), "Previous page")
                : Component.text("«", NamedTextColor.DARK_GRAY);
        Component next = current < total
                ? clickRun(Component.text("»", NamedTextColor.YELLOW), commandBase + " " + (current + 1), "Next page")
                : Component.text("»", NamedTextColor.DARK_GRAY);
        Component page = Component.text(" Page " + current + "/" + total + " ", NamedTextColor.GRAY);
        if (current < total) {
            page = clickRun(page, commandBase + " " + (current + 1), "Next page");
        } else if (current > 1) {
            page = clickRun(page, commandBase + " " + (current - 1), "Previous page");
        }
        return prev.append(page).append(next);
    }

    public record Line(String label, String value) {
    }
}
