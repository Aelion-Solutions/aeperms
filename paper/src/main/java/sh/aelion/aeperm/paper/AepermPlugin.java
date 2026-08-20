package sh.aelion.aeperm.paper;

import lombok.Getter;
import sh.aelion.aeperm.api.AepermAPI;
import sh.aelion.aeperm.api.AepermProvider;
import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.api.Wildcard;
import sh.aelion.aeperm.common.AepermBootstrap;
import sh.aelion.aeperm.common.command.subcommand.CommandMeta;
import sh.aelion.aeperm.common.command.CommandService;
import sh.aelion.aeperm.common.service.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class AepermPlugin extends JavaPlugin {

    private AepermBootstrap bootstrap;
    @Getter
    private PermissionService permissions;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> lastAttached = new ConcurrentHashMap<>();
    private final Map<UUID, ContextSet> playerContexts = new ConcurrentHashMap<>();
    private final Set<UUID> injected = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean expandWildcards = new AtomicBoolean();
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
            AepermProvider.register(permissions);
            Bukkit.getPluginManager().registerEvents(new AepermListener(this), this);
            AepermCommands.register(this, commands);

            for (Player player : Bukkit.getOnlinePlayers()) {
                rememberContext(player);
                ensureInjected(player);
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
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            clearAttachment(player);
        }
        new HashSet<>(injected).forEach(this::clearAttachment);
        new HashSet<>(attachments.keySet()).forEach(this::clearAttachment);
        AepermProvider.unregister();
        Bukkit.getServicesManager().unregisterAll(this);
        if (bootstrap != null) {
            bootstrap.close();
        }
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

    public void preloadUser(UUID uuid) {
        if (permissions == null) {
            return;
        }
        try {
            permissions.user(uuid);
        } catch (RuntimeException e) {
            getLogger().warning("Failed to preload permissions for " + uuid + ": " + e.getMessage());
        }
    }

    void ensureInjected(Player player) {
        UUID uuid = player.getUniqueId();
        if (injected.contains(uuid) || expandWildcards.get()) {
            return;
        }
        if (PermissibleInjector.inject(player, new AepermPermissible(player, this))) {
            injected.add(uuid);
            return;
        }
        if (expandWildcards.compareAndSet(false, true)) {
            getLogger().warning("Could not intercept Bukkit permission checks; expanding wildcards onto registered Superperms nodes instead");
        }
    }

    Map<String, Boolean> cachedPermissions(UUID uuid) {
        if (permissions == null) {
            return null;
        }
        ContextSet ctx = playerContexts.get(uuid);
        Optional<CalculatedUser> user = ctx == null
                ? permissions.cache().userAny(uuid)
                : permissions.cache().user(uuid, ctx);
        if (user.isEmpty()) {
            user = permissions.cache().userAny(uuid);
        }
        return user.map(CalculatedUser::permissions).orElse(null);
    }

    public void loadAndAttach(Player player) {
        rememberContext(player);
        ensureInjected(player);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            permissions.updateUserName(player.getUniqueId(), player.getName());
            CalculatedUser user = permissions.user(player.getUniqueId()).orElseThrow();
            Bukkit.getScheduler().runTask(this, () -> applyAttachment(player, user));
        });
    }

    void reattachAllIfExpanding() {
        if (!expandWildcards.get()) {
            return;
        }
        reattachAll();
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

    public void clearAttachment(Player player) {
        clearAttachment(player.getUniqueId(), player);
    }

    public void clearAttachment(UUID uuid) {
        clearAttachment(uuid, Bukkit.getPlayer(uuid));
    }

    private void clearAttachment(UUID uuid, Player player) {
        playerContexts.remove(uuid);
        lastAttached.remove(uuid);
        injected.remove(uuid);
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Exception ignored) {
            }
        }
        if (player != null) {
            PermissibleInjector.uninject(player);
        }
    }

    private void applyAttachment(Player player, CalculatedUser user) {
        Map<String, Boolean> next = expandWildcards.get()
                ? Wildcard.expand(user.permissions(), knownPermissionNames())
                : user.permissions();
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

    private static Iterable<String> knownPermissionNames() {
        return Bukkit.getPluginManager().getPermissions().stream()
                .map(Permission::getName)
                .toList();
    }
}
