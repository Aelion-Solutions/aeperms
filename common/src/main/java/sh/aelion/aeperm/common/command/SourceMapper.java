package sh.aelion.aeperm.common.command;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import sh.aelion.aeperm.common.history.ActingContext;
import sh.aelion.aeperm.common.history.Actor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class SourceMapper {

    private SourceMapper() {
    }

    public static <S> LiteralCommandNode<S> map(
            LiteralCommandNode<AepermSource> node,
            Function<S, AepermSource> adaptor
    ) {
        return map(node, adaptor, ignored -> true);
    }

    public static <S> LiteralCommandNode<S> map(
            LiteralCommandNode<AepermSource> node,
            Function<S, AepermSource> adaptor,
            Predicate<S> visible
    ) {
        LiteralArgumentBuilder<S> builder = LiteralArgumentBuilder.literal(node.getLiteral());
        apply(node, builder, adaptor, visible);
        return builder.build();
    }

    private static <S, T> CommandNode<S> mapArgument(
            ArgumentCommandNode<AepermSource, T> node,
            Function<S, AepermSource> adaptor,
            Predicate<S> visible
    ) {
        RequiredArgumentBuilder<S, T> builder = RequiredArgumentBuilder.argument(
                node.getName(),
                node.getType()
        );
        SuggestionProvider<AepermSource> suggestions = node.getCustomSuggestions();
        if (suggestions != null) {
            builder.suggests((ctx, suggestionBuilder) ->
                    suggestions.getSuggestions(mapContext(ctx, adaptor), suggestionBuilder));
        }
        apply(node, builder, adaptor, visible);
        return builder.build();
    }

    private static <S> void apply(
            CommandNode<AepermSource> node,
            ArgumentBuilder<S, ?> builder,
            Function<S, AepermSource> adaptor,
            Predicate<S> visible
    ) {
        builder.requires(source -> visible.test(source) && node.getRequirement().test(adaptor.apply(source)));
        if (node.getCommand() != null) {
            com.mojang.brigadier.Command<AepermSource> command = node.getCommand();
            builder.executes(ctx -> {
                AepermSource source = adaptor.apply(ctx.getSource());
                try {
                    return ActingContext.callThrowing(
                            Actor.command(source.actorName()),
                            () -> command.run(mapContext(ctx, adaptor))
                    );
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    if (e instanceof com.mojang.brigadier.exceptions.CommandSyntaxException syntax) {
                        throw syntax;
                    }
                    throw new RuntimeException(e);
                }
            });
        }
        for (CommandNode<AepermSource> child : node.getChildren()) {
            builder.then(mapChild(child, adaptor, visible));
        }
    }

    @SuppressWarnings("unchecked")
    private static <S> CommandNode<S> mapChild(
            CommandNode<AepermSource> child,
            Function<S, AepermSource> adaptor,
            Predicate<S> visible
    ) {
        if (child instanceof LiteralCommandNode<AepermSource> literal) {
            return map(literal, adaptor, visible);
        }
        if (child instanceof ArgumentCommandNode<?, ?> argument) {
            return mapArgument((ArgumentCommandNode<AepermSource, ?>) argument, adaptor, visible);
        }
        throw new IllegalArgumentException("Unsupported command node " + child.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <S> CommandContext<AepermSource> mapContext(
            CommandContext<S> ctx,
            Function<S, AepermSource> adaptor
    ) {
        CommandContext<S> child = ctx.getChild();
        return new CommandContext<>(
                adaptor.apply(ctx.getSource()),
                ctx.getInput(),
                copyArguments(ctx),
                (com.mojang.brigadier.Command<AepermSource>) ctx.getCommand(),
                (CommandNode<AepermSource>) ctx.getRootNode(),
                (List) ctx.getNodes(),
                ctx.getRange(),
                child == null ? null : mapContext(child, adaptor),
                (RedirectModifier<AepermSource>) ctx.getRedirectModifier(),
                ctx.isForked()
        );
    }

    private static <S> Map<String, ParsedArgument<AepermSource, ?>> copyArguments(CommandContext<S> ctx) {
        Map<String, ParsedArgument<AepermSource, ?>> arguments = new LinkedHashMap<>();
        for (ParsedCommandNode<S> parsed : ctx.getNodes()) {
            if (parsed.getNode() instanceof ArgumentCommandNode<?, ?> argument) {
                String name = argument.getName();
                Object value = ctx.getArgument(name, Object.class);
                arguments.put(name, new ParsedArgument<>(
                        parsed.getRange().getStart(),
                        parsed.getRange().getEnd(),
                        value
                ));
            }
        }
        return arguments;
    }
}
