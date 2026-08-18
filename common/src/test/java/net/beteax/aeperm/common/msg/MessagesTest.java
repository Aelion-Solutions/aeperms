package net.beteax.aeperm.common.msg;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

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
    void cardHelpersFormatHeaderAndLines() {
        String header = PlainTextComponentSerializer.plainText().serialize(Messages.cardHeader("Plugin Info"));
        String line = PlainTextComponentSerializer.plainText().serialize(Messages.cardLine("Version", "1.0"));
        assertThat(header).contains("AePerm").contains("Plugin Info");
        assertThat(line).contains("Version").contains("1.0");
    }
}
