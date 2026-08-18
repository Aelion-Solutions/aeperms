package sh.aelion.aeperm.common.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionNodeArgumentTest {

    @Test
    void parsesStarAndNestedWildcards() throws CommandSyntaxException {
        assertThat(parse("*")).isEqualTo("*");
        assertThat(parse("\"*\"")).isEqualTo("*");
        assertThat(parse("mod.*")).isEqualTo("mod.*");
        assertThat(parse("-*")).isEqualTo("-*");
        assertThat(parse("a.b.c")).isEqualTo("a.b.c");
    }

    @Test
    void stopsAtWhitespaceSoTtlCanFollow() throws CommandSyntaxException {
        StringReader reader = new StringReader("* 60");
        assertThat(PermissionNodeArgument.permissionNode().parse(reader)).isEqualTo("*");
        assertThat(reader.canRead()).isTrue();
        assertThat(reader.peek()).isEqualTo(' ');
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(CommandSyntaxException.class);
    }

    private static String parse(String input) throws CommandSyntaxException {
        return PermissionNodeArgument.permissionNode().parse(new StringReader(input));
    }
}
