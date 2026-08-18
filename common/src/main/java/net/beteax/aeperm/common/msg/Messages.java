package net.beteax.aeperm.common.msg;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<color:#a200ff>AePerm</color> <gray>»</gray> ";

    private Messages() {
    }

    public static Component info(String body) {
        return MM.deserialize(PREFIX + "<gray>" + body + "</gray>");
    }

    public static Component error(String body) {
        return MM.deserialize(PREFIX + "<red>" + body + "</red>");
    }

    public static Component success(String body) {
        return MM.deserialize(PREFIX + "<green>" + body + "</green>");
    }

    public static Component cardHeader(String title) {
        return MM.deserialize(PREFIX + "<yellow>" + title + "</yellow>");
    }

    public static Component cardLine(String label, String value) {
        return MM.deserialize("<gray>  " + label + ": <yellow>" + value + "</yellow></gray>");
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

    public static void card(Audience audience, String title, List<Line> lines) {
        audience.sendMessage(cardHeader(title));
        for (Line line : lines) {
            audience.sendMessage(cardLine(line.label(), line.value()));
        }
    }

    public static String prefixRaw() {
        return PREFIX;
    }

    public record Line(String label, String value) {
    }
}
