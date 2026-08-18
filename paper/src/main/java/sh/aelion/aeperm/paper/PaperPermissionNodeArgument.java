package sh.aelion.aeperm.paper;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import sh.aelion.aeperm.common.command.PermissionNodeArgument;

/**
 * Paper requires custom Brigadier types to implement {@link CustomArgumentType}.
 * Native type is {@link StringArgumentType#string()} so clients can send quoted {@code "*"}.
 * Server parse still accepts unquoted {@code *} (console / RCON).
 */
public final class PaperPermissionNodeArgument implements CustomArgumentType<String, String> {

    static final PaperPermissionNodeArgument INSTANCE = new PaperPermissionNodeArgument();

    private PaperPermissionNodeArgument() {
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return PermissionNodeArgument.permissionNode().parse(reader);
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }
}
