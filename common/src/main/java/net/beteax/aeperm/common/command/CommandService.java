package net.beteax.aeperm.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.beteax.aeperm.common.command.subcommand.CommandContext;
import net.beteax.aeperm.common.command.subcommand.CommandMeta;
import net.beteax.aeperm.common.history.ActingContext;
import net.beteax.aeperm.common.history.Actor;
import net.beteax.aeperm.common.msg.Messages;
import net.beteax.aeperm.common.service.PermissionService;
import net.kyori.adventure.audience.Audience;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CommandService {

    private final CommandContext ctx;
    private final CommandDispatcher<AepermSource> dispatcher;
    private final LiteralCommandNode<AepermSource> root;

    public CommandService(
            PermissionService permissions,
            Function<String, Optional<UUID>> nameResolver,
            Supplier<Collection<String>> onlineNames,
            CommandMeta meta
    ) {
        this.ctx = new CommandContext(permissions, nameResolver, onlineNames, meta);
        this.root = new AepermCommand(ctx).build();
        this.dispatcher = new CommandDispatcher<>();
        this.dispatcher.getRoot().addChild(root);
    }

    public CommandService(PermissionService permissions, Function<String, Optional<UUID>> nameResolver) {
        this(permissions, nameResolver, List::of, CommandMeta.of("unknown", false));
    }

    public LiteralCommandNode<AepermSource> rootNode() {
        return root;
    }

    public CommandDispatcher<AepermSource> dispatcher() {
        return dispatcher;
    }

    public void handle(Audience sender, String[] args) {
        AepermSource source = new AepermSource(sender, ignored -> true);
        ActingContext.run(Actor.command(source.actorName()), () -> {
            try {
                dispatcher.execute(inputFor(args), source);
            } catch (CommandSyntaxException e) {
                Messages.error(sender, friendly(args, e));
            }
        });
    }

    public List<String> suggestRoot(String partial) {
        return suggest(new String[]{partial == null ? "" : partial});
    }

    public List<String> suggestGroups(String partial) {
        return filter(new ArrayList<>(ctx.permissions().groupNames()), partial);
    }

    public List<String> suggest(String[] parts) {
        ParseResults<AepermSource> parsed = dispatcher.parse(inputFor(parts), suggestSource());
        return dispatcher.getCompletionSuggestions(parsed).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }

    private AepermSource suggestSource() {
        return new AepermSource(Audience.empty(), ignored -> true);
    }

    static String inputFor(String[] parts) {
        if (parts == null || parts.length == 0) {
            return "aeperm";
        }
        return "aeperm " + String.join(" ", parts);
    }

    private static String friendly(String[] args, CommandSyntaxException e) {
        if (args != null && args.length > 0) {
            String first = args[0];
            if (first != null && !first.isBlank()) {
                String lower = first.toLowerCase(Locale.ROOT);
                if (!List.of("user", "group", "cache", "sync", "history", "info").contains(lower)) {
                    return "Unknown subcommand <yellow>" + first + "</yellow>";
                }
            }
        }
        String message = e.getRawMessage() == null ? e.getMessage() : e.getRawMessage().getString();
        return message == null || message.isBlank() ? "Invalid command" : message;
    }

    private static List<String> filter(List<String> values, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(value);
            }
        }
        return out;
    }
}
