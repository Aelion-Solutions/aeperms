package sh.aelion.aeperm.paper;

import lombok.Getter;
import sh.aelion.aeperm.api.AepermAPI;
import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.common.AepermBootstrap;
import sh.aelion.aeperm.common.command.subcommand.CommandMeta;
import sh.aelion.aeperm.common.command.CommandService;
import sh.aelion.aeperm.common.service.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class AepermPlugin extends JavaPlugin {

    private AepermBootstrap bootstrap;
    @Getter
    private PermissionService permissions;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> lastAttached = new ConcurrentHashMap<>();
    private final Map<UUID, ContextSet> playerContexts = new ConcurrentHashMap<>();
    private final AtomicReference<String> serverId = new AtomicReference<>("server-1");

    @Override
    public void onEnable() {
        try {
            bootstrap = AepermBootstrap.create(
                    getDataFolder().toPath(),
                    getLogger(),
                    this::contextFor,
                    event -> Bukkit.getScheduler().runTask(this, () -> reattach(event.uuid())),
                    event -> Bukkit.getScheduler().runTask(this, () -> reattachGroup(event.group())),
                    src -> Bukkit.getScheduler().runTask(this, this::reattachAll),
                    false
            );
            serverId.set(bootstrap.config().serverId());
            permissions = bootstrap.permissions();
            CommandService commands = new CommandService(
                    permissions,
                    name -> {
                        Player player = Bukkit.getPlayerExact(name);
                        return player == null ? Optional.empty() : Optional.of(player.getUniqueId());
                    },
                    () -> Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                    CommandMeta.of(getPluginMeta().getVersion(), bootstrap.config().serversync().enabled())
            );

            Bukkit.getServicesManager().register(AepermAPI.class, permissions, this, ServicePriority.Normal);
            Bukkit.getPluginManager().registerEvents(new AepermListener(this), this);
            AepermCommands.register(this, commands);

            for (Player player : Bukkit.getOnlinePlayers()) {
                rememberContext(player);
                loadAndAttach(player);
            }
            getLogger().info("AePerm enabled");
        } catch (Exception e) {
            getLogger().severe("Failed to enable AePerm: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        attachments.keySet().forEach(this::clearAttachment);
        if (bootstrap != null) {
            bootstrap.close();
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }

    public void rememberContext(Player player) {
        ContextSet ctx = ContextSet.builder()
                .server(serverId.get())
                .world(player.getWorld().getName())
                .build();
        ContextSet previous = playerContexts.put(player.getUniqueId(), ctx);
        if (previous != null && !previous.equals(ctx)) {
            permissions.invalidateUser(player.getUniqueId());
        }
    }

    private ContextSet contextFor(UUID uuid) {
        ContextSet cached = playerContexts.get(uuid);
        if (cached != null) {
            return cached;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            rememberContext(player);
            return playerContexts.getOrDefault(uuid, ContextSet.builder().server(serverId.get()).build());
        }
        return ContextSet.builder().server(serverId.get()).build();
    }

    public void loadAndAttach(Player player) {
        rememberContext(player);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            permissions.updateUserName(player.getUniqueId(), player.getName());
            CalculatedUser user = permissions.user(player.getUniqueId()).orElseThrow();
            Bukkit.getScheduler().runTask(this, () -> applyAttachment(player, user));
        });
    }

    public void reattach(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            permissions.invalidateUser(uuid);
            return;
        }
        loadAndAttach(player);
    }

    public void reattachGroup(String group) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            CalculatedUser user = permissions.cache().userAny(player.getUniqueId()).orElse(null);
            if (permissions.cache().userAffectedByGroup(user, group)) {
                loadAndAttach(player);
            }
        }
    }

    public void reattachAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadAndAttach(player);
        }
    }

    public void clearAttachment(UUID uuid) {
        playerContexts.remove(uuid);
        lastAttached.remove(uuid);
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Exception ignored) {
            }
        }
    }

    private void applyAttachment(Player player, CalculatedUser user) {
        Map<String, Boolean> next = user.permissions();
        PermissionAttachment attachment = attachments.get(player.getUniqueId());
        Map<String, Boolean> prev = lastAttached.getOrDefault(player.getUniqueId(), Map.of());
        if (attachment == null) {
            attachment = player.addAttachment(this);
            attachments.put(player.getUniqueId(), attachment);
            for (Map.Entry<String, Boolean> entry : next.entrySet()) {
                attachment.setPermission(entry.getKey(), entry.getValue());
            }
            lastAttached.put(player.getUniqueId(), Map.copyOf(next));
            player.recalculatePermissions();
            return;
        }
        boolean changed = false;
        for (String key : new HashMap<>(prev).keySet()) {
            if (!next.containsKey(key)) {
                attachment.unsetPermission(key);
                changed = true;
            }
        }
        for (Map.Entry<String, Boolean> entry : next.entrySet()) {
            if (!Objects.equals(prev.get(entry.getKey()), entry.getValue())) {
                attachment.setPermission(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        lastAttached.put(player.getUniqueId(), Map.copyOf(next));
        if (changed) {
            player.recalculatePermissions();
        }
    }
}
