package sh.aelion.aeperm.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import sh.aelion.aeperm.common.command.subcommand.CommandContext;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class Arguments {

    static final int SUGGEST_LIMIT = 50;

    private Arguments() {
    }

    public static RequiredArgumentBuilder<AepermSource, String> word(String name) {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.word());
    }

    /** Permission node token. Allows {@code *} unlike {@link #word(String)}. */
    public static RequiredArgumentBuilder<AepermSource, String> node(String name) {
        return RequiredArgumentBuilder.argument(name, PermissionNodeArgument.permissionNode());
    }

    public static RequiredArgumentBuilder<AepermSource, Integer> integer(String name) {
        return RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer());
    }

    public static RequiredArgumentBuilder<AepermSource, Long> seconds(String name) {
        return RequiredArgumentBuilder.argument(name, LongArgumentType.longArg(0));
    }

    public static RequiredArgumentBuilder<AepermSource, String> player(String name, CommandContext ctx) {
        return word(name).suggests((commandContext, builder) ->
                suggest(builder, playerNames(ctx, builder.getRemaining())));
    }

    public static RequiredArgumentBuilder<AepermSource, String> group(String name, CommandContext ctx) {
        return word(name).suggests((commandContext, builder) -> suggest(builder, ctx.permissions().groupNames()));
    }

    private static Collection<String> playerNames(CommandContext ctx, String remaining) {
        String prefix = remaining == null ? "" : remaining;
        String needle = prefix.toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (String online : ctx.onlineNames()) {
            if (online != null && online.toLowerCase(Locale.ROOT).startsWith(needle)) {
                names.add(online);
                if (names.size() >= SUGGEST_LIMIT) {
                    return names;
                }
            }
        }
        if (prefix.isEmpty()) {
            return names;
        }
        int remainingSlots = SUGGEST_LIMIT - names.size();
        if (remainingSlots > 0) {
            names.addAll(ctx.permissions().storage().listUserNames(prefix, remainingSlots));
        }
        return names;
    }

    static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> values) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        int count = 0;
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value);
                if (++count >= SUGGEST_LIMIT) {
                    break;
                }
            }
        }
        return builder.buildFuture();
    }
}
