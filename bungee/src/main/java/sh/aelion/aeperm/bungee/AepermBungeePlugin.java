package sh.aelion.aeperm.bungee;

import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.common.AepermBootstrap;
import sh.aelion.aeperm.common.command.CommandService;
import sh.aelion.aeperm.common.command.subcommand.CommandMeta;
import sh.aelion.aeperm.common.history.ActingContext;
import sh.aelion.aeperm.common.history.Actor;
import sh.aelion.aeperm.common.msg.Messages;
import sh.aelion.aeperm.common.service.PermissionService;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PermissionCheckEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class AepermBungeePlugin extends Plugin implements Listener {

    private AepermBootstrap bootstrap;
    private CommandService commands;
    private PermissionService permissions;
    private BungeeAudiences audiences;
    private final AtomicReference<String> serverId = new AtomicReference<>("proxy-1");

    @Override
    public void onEnable() {
        try {
            audiences = BungeeAudiences.create(this);
            bootstrap = AepermBootstrap.create(
                    getDataFolder().toPath(),
                    getLogger(),
                    uuid -> ContextSet.builder().server(serverId.get()).proxy(serverId.get()).build(),
                    e -> {
                    },
                    e -> {
                    },
                    false
            );
            serverId.set(bootstrap.config().serverId());
            permissions = bootstrap.permissions();
            commands = new CommandService(
                    permissions,
                    name -> {
                        ProxiedPlayer player = getProxy().getPlayer(name);
                        return player == null ? Optional.empty() : Optional.of(player.getUniqueId());
                    },
                    () -> getProxy().getPlayers().stream().map(ProxiedPlayer::getName).collect(Collectors.toList()),
                    CommandMeta.of(getDescription().getVersion(), bootstrap.config().serversync().enabled())
            );

            getProxy().getPluginManager().registerCommand(this, new AepermCommand());
            getProxy().getPluginManager().registerListener(this, this);
            getLogger().info("AePerm enabled!");
        } catch (Exception e) {
            getLogger().severe("Failed to enable AePerm: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (audiences != null) {
            audiences.close();
        }
        if (bootstrap != null) {
            bootstrap.close();
        }
    }

    @EventHandler
    public void onPermissionCheck(PermissionCheckEvent event) {
        if (permissions == null || !(event.getSender() instanceof ProxiedPlayer player)) {
            return;
        }
        event.setHasPermission(permissions.has(player.getUniqueId(), event.getPermission()));
    }

    private final class AepermCommand extends Command implements TabExecutor {

        private AepermCommand() {
            super("aeperm", "aeperm.admin", "ap");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer) {
                return;
            }
            if (!sender.hasPermission("aeperm.admin")) {
                Messages.error(audiences.sender(sender), "No permission");
                return;
            }
            ActingContext.run(Actor.command(sender.getName()), () -> commands.handle(audiences.sender(sender), args));
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer) {
                return List.of();
            }
            return commands.suggest(args);
        }
    }
}
