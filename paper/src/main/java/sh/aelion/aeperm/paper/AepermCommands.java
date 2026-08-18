package sh.aelion.aeperm.paper;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import sh.aelion.aeperm.common.command.AepermSource;
import sh.aelion.aeperm.common.command.CommandService;
import sh.aelion.aeperm.common.command.PermissionNodeArgument;
import sh.aelion.aeperm.common.command.SourceMapper;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.function.Function;

public final class AepermCommands {

    private static final Function<ArgumentType<?>, ArgumentType<?>> PAPER_TYPES = type ->
            type instanceof PermissionNodeArgument
                    ? PaperPermissionNodeArgument.INSTANCE
                    : type;

    private AepermCommands() {
    }

    public static void register(AepermPlugin plugin, CommandService commands) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LiteralCommandNode<CommandSourceStack> root = SourceMapper.map(
                    commands.rootNode(),
                    stack -> {
                        CommandSender sender = stack.getSender();
                        return new AepermSource(
                                (Audience) sender,
                                sender::hasPermission,
                                sender.getName()
                        );
                    },
                    ignored -> true,
                    PAPER_TYPES
            );
            event.registrar().register(root, "AePerm", List.of("ap"));
        });
    }
}
