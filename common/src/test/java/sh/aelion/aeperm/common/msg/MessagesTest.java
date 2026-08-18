package sh.aelion.aeperm.common.msg;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesTest {

    @Test
    void prefixesBrand() {
        String info = PlainTextComponentSerializer.plainText().serialize(Messages.info("hello <yellow>there</yellow>"));
        String error = PlainTextComponentSerializer.plainText().serialize(Messages.error("bad"));
        String success = PlainTextComponentSerializer.plainText().serialize(Messages.success("ok"));
        assertThat(info).contains("AePerm").contains("»").contains("hello");
        assertThat(error).contains("AePerm").contains("bad");
        assertThat(success).contains("AePerm").contains("ok");
        assertThat(Messages.prefixRaw()).contains("#a200ff");
    }

    @Test
    void framedHelpersUseBareArrow() {
        String header = PlainTextComponentSerializer.plainText().serialize(Messages.cardHeader("Plugin Info"));
        String line = PlainTextComponentSerializer.plainText().serialize(Messages.cardLine("Version", "1.0"));
        String sep = PlainTextComponentSerializer.plainText().serialize(Messages.separator());
        assertThat(header).contains("»").contains("Plugin Info").doesNotContain("AePerm");
        assertThat(line).contains("Version").contains("1.0").doesNotContain("AePerm");
        assertThat(sep).contains("»").contains("─");
    }

    @Test
    void clickRunAttachesCommand() {
        Component link = Messages.groupLink("staff");
        assertThat(link.clickEvent()).isNotNull();
        assertThat(Objects.requireNonNull(link.clickEvent()).action()).isEqualTo(ClickEvent.Action.RUN_COMMAND);
        assertThat(Objects.requireNonNull(link.clickEvent()).value()).isEqualTo("/ap group staff");
    }

    @Test
    void permissionPagePaginates() {
        Map<String, Boolean> nodes = Map.of(
                "a.one", true,
                "a.two", false,
                "b.three", true
        );
        List<Component> messages = new ArrayList<>();
        Messages.permissionPage(new Audience() {
            @Override
            public void sendMessage(@NotNull Component message) {
                messages.add(message);
            }
        }, "Permissions", nodes, 1, "/ap group staff permissions");
        String joined = messages.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(joined).contains("Permissions").contains("+").contains("-");
    }
}
