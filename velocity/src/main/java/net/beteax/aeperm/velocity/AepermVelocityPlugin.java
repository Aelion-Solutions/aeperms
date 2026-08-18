package net.beteax.aeperm.velocity;

import com.google.inject.Inject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.common.AepermBootstrap;
import net.beteax.aeperm.common.command.AepermSource;
import net.beteax.aeperm.common.command.CommandService;
import net.beteax.aeperm.common.command.SourceMapper;
import net.beteax.aeperm.common.command.subcommand.CommandMeta;
import net.beteax.aeperm.common.service.PermissionService;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;

@Plugin(id = "aeperm", name = "AePerm", version = "1.0-SNAPSHOT", authors = {"Variiuz"})
public final class AepermVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginContainer container;
    private AepermBootstrap bootstrap;
    private CommandService commands;
    private PermissionService permissions;
    private final AtomicReference<String> serverId = new AtomicReference<>("proxy-1");

    @Inject
    public AepermVelocityPlugin(
            ProxyServer server,
            Logger logger,
            @DataDirectory Path dataDirectory,
            PluginContainer container
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.container = container;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        try {
            java.util.logging.Logger jul = new java.util.logging.Logger("aeperm", null) {
                {
                    setLevel(Level.ALL);
                }

                @Override
                public void log(LogRecord record) {
                    if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                        logger.error(record.getMessage());
                    } else if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        logger.warn(record.getMessage());
                    } else {
                        logger.info(record.getMessage());
                    }
                }
            };

            bootstrap = AepermBootstrap.create(
                    dataDirectory,
                    jul,
                    uuid -> ContextSet.builder()
                            .server(serverId.get())
                            .proxy(serverId.get())
                            .build(),
                    e -> {
                    },
                    e -> {
                    },
                    false
            );
            serverId.set(bootstrap.config().serverId());

            permissions = bootstrap.permissions();
            String version = container.getDescription().getVersion().orElse("unknown");
            commands = new CommandService(
                    permissions,
                    name -> server.getPlayer(name).map(Player::getUniqueId),
                    () -> server.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList()),
                    CommandMeta.of(version, bootstrap.config().serversync().enabled())
            );

            LiteralCommandNode<CommandSource> mapped = SourceMapper.map(
                    commands.rootNode(),
                    source -> new AepermSource(
                            source,
                            source::hasPermission,
                            source instanceof Player player ? player.getUsername() : "console"
                    ),
                    source -> !(source instanceof Player)
            );
            LiteralArgumentBuilder<CommandSource> root = BrigadierCommand.literalArgumentBuilder("aeperm")
                    .requires(mapped.getRequirement())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            return BrigadierCommand.FORWARD;
                        }
                        if (mapped.getCommand() == null) {
                            return 0;
                        }
                        return mapped.getCommand().run(ctx);
                    });
            for (CommandNode<CommandSource> child : mapped.getChildren()) {
                root.then(child);
            }

            BrigadierCommand command = new BrigadierCommand(root.build());
            var meta = server.getCommandManager()
                    .metaBuilder(command)
                    .aliases("ap")
                    .plugin(this)
                    .build();
            server.getCommandManager().register(meta, command);

            logger.info("AePerm Velocity enabled (commands + permission provider)");
        } catch (Exception e) {
            logger.error("Failed to enable AePerm", e);
        }
    }

    @Subscribe
    public void onPermissions(PermissionsSetupEvent event) {
        if (permissions == null) {
            return;
        }
        event.setProvider(subject -> {
            if (!(subject instanceof Player player)) {
                return PermissionFunction.ALWAYS_TRUE;
            }
            return permission -> permissions.has(player.getUniqueId(), permission)
                    ? Tristate.TRUE
                    : Tristate.FALSE;
        });
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (bootstrap != null) {
            bootstrap.close();
        }
    }
}
