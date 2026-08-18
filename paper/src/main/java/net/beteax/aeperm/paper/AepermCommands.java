package net.beteax.aeperm.paper;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.beteax.aeperm.common.command.AepermSource;
import net.beteax.aeperm.common.command.CommandService;
import net.beteax.aeperm.common.command.SourceMapper;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class AepermCommands {

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
                    }
            );
            event.registrar().register(root, "AePerm", List.of("ap"));
        });
    }
}
