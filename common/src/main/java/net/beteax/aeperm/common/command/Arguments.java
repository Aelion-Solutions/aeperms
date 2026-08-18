package net.beteax.aeperm.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.beteax.aeperm.common.command.subcommand.CommandContext;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class Arguments {

    private Arguments() {
    }

    public static RequiredArgumentBuilder<AepermSource, String> word(String name) {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.word());
    }

    public static RequiredArgumentBuilder<AepermSource, Integer> integer(String name) {
        return RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer());
    }

    public static RequiredArgumentBuilder<AepermSource, Long> seconds(String name) {
        return RequiredArgumentBuilder.argument(name, LongArgumentType.longArg(0));
    }

    public static RequiredArgumentBuilder<AepermSource, String> player(String name, CommandContext ctx) {
        return word(name).suggests((commandContext, builder) -> suggest(builder, playerNames(ctx)));
    }

    public static RequiredArgumentBuilder<AepermSource, String> group(String name, CommandContext ctx) {
        return word(name).suggests((commandContext, builder) -> suggest(builder, ctx.permissions().groupNames()));
    }

    private static Collection<String> playerNames(CommandContext ctx) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(ctx.onlineNames());
        names.addAll(ctx.permissions().storage().listUserNames());
        return names;
    }

    static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> values) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }
}
