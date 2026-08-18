package sh.aelion.aeperm.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceMapperTest {

    private record Dummy(String id) {
    }

    @Test
    void mapsExecuteAndCurrentTokenSuggestions() throws CommandSyntaxException {
        List<Component> messages = new ArrayList<>();
        Audience audience = new Audience() {
            @Override
            public void sendMessage(@NotNull Component message) {
                messages.add(message);
            }
        };

        LiteralCommandNode<AepermSource> original = LiteralArgumentBuilder.<AepermSource>literal("aeperm")
                .then(LiteralArgumentBuilder.<AepermSource>literal("ping")
                        .executes(ctx -> {
                            ctx.getSource().audience().sendMessage(Component.text("pong"));
                            return 1;
                        }))
                .then(RequiredArgumentBuilder.<AepermSource, String>argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("alpha");
                            builder.suggest("beta");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ctx.getSource().audience().sendMessage(Component.text(StringArgumentType.getString(ctx, "name")));
                            return 1;
                        }))
                .build();

        LiteralCommandNode<Dummy> mapped = SourceMapper.map(
                original,
                dummy -> new AepermSource(audience, ignored -> true)
        );
        CommandDispatcher<Dummy> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(mapped);

        dispatcher.execute("aeperm ping", new Dummy("ok"));
        dispatcher.execute("aeperm hello", new Dummy("ok"));

        String joined = messages.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(joined).contains("pong").contains("hello");

        List<String> suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("aeperm a", new Dummy("ok")))
                .join()
                .getList()
                .stream()
                .map(Suggestion::getText)
                .toList();
        assertThat(suggestions).contains("alpha").doesNotContain("aeperm alpha");
    }

    @Test
    void visibilityPredicateHidesMappedTree() {
        LiteralCommandNode<AepermSource> original = LiteralArgumentBuilder.<AepermSource>literal("aeperm")
                .executes(ctx -> 1)
                .build();
        LiteralCommandNode<Dummy> mapped = SourceMapper.map(
                original,
                dummy -> new AepermSource(Audience.empty(), ignored -> true),
                dummy -> dummy.id().equals("console")
        );
        CommandDispatcher<Dummy> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(mapped);

        assertThat(mapped.getRequirement().test(new Dummy("console"))).isTrue();
        assertThat(mapped.getRequirement().test(new Dummy("player"))).isFalse();
        assertThatThrownBy(() -> dispatcher.execute("aeperm", new Dummy("player")))
                .isInstanceOf(CommandSyntaxException.class);
    }
}
