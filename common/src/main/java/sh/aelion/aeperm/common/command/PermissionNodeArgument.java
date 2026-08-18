package sh.aelion.aeperm.common.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.List;

/**
 * Permission node token. Like word(), but allows {@code *} and quoted forms such as {@code "*"}.
 */
public final class PermissionNodeArgument implements ArgumentType<String> {

    private static final PermissionNodeArgument INSTANCE = new PermissionNodeArgument();
    private static final Collection<String> EXAMPLES = List.of("*", "\"*\"", "mod.*", "a.b.c", "-mod.use");

    private PermissionNodeArgument() {
    }

    public static PermissionNodeArgument permissionNode() {
        return INSTANCE;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '"') {
            return reader.readQuotedString();
        }
        int start = reader.getCursor();
        while (reader.canRead() && isAllowed(reader.peek())) {
            reader.skip();
        }
        if (reader.getCursor() == start) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, "permission node");
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    static boolean isAllowed(char c) {
        return StringReader.isAllowedInUnquotedString(c) || c == '*';
    }
}
