package net.beteax.aeperm.common.service;

import net.beteax.aeperm.api.AepermAPI;
import net.beteax.aeperm.api.CalculatedGroup;
import net.beteax.aeperm.api.CalculatedUser;
import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.api.PermissionNode;
import net.beteax.aeperm.api.Wildcard;
import net.beteax.aeperm.api.event.GroupChangedEvent;
import net.beteax.aeperm.api.event.PermissionChangedEvent;
import net.beteax.aeperm.common.cache.LocalCache;
import net.beteax.aeperm.common.calc.PermissionCalculator;
import net.beteax.aeperm.common.history.ActingContext;
import net.beteax.aeperm.common.history.Actor;
import net.beteax.aeperm.common.history.HistoryRecord;
import net.beteax.aeperm.common.model.GroupData;
import net.beteax.aeperm.common.model.UserData;
import net.beteax.aeperm.common.storage.Storage;
import net.beteax.aeperm.common.sync.SyncBus;
import net.beteax.aeperm.common.sync.SyncMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class PermissionService implements AepermAPI {

    private final Storage storage;
    private final LocalCache cache;
    private final PermissionCalculator calculator;
    private final SyncBus syncBus;
    private final ContextProvider contexts;
    private final Clock clock;
    private final Executor async;
    private final Consumer<PermissionChangedEvent> permissionListener;
    private final Consumer<GroupChangedEvent> groupListener;
    private final Consumer<String> reloadListener;

    public PermissionService(
            Storage storage,
            LocalCache cache,
            PermissionCalculator calculator,
            SyncBus syncBus,
            ContextProvider contexts,
            Clock clock,
            Executor async,
            Consumer<PermissionChangedEvent> permissionListener,
            Consumer<GroupChangedEvent> groupListener
    ) {
        this(storage, cache, calculator, syncBus, contexts, clock, async, permissionListener, groupListener, src -> {
        });
    }

    public PermissionService(
            Storage storage,
            LocalCache cache,
            PermissionCalculator calculator,
            SyncBus syncBus,
            ContextProvider contexts,
            Clock clock,
            Executor async,
            Consumer<PermissionChangedEvent> permissionListener,
            Consumer<GroupChangedEvent> groupListener,
            Consumer<String> reloadListener
    ) {
        this.storage = storage;
        this.cache = cache;
        this.calculator = calculator;
        this.syncBus = syncBus;
        this.contexts = contexts;
        this.clock = clock;
        this.async = async;
        this.permissionListener = permissionListener == null ? e -> {
        } : permissionListener;
        this.groupListener = groupListener == null ? e -> {
        } : groupListener;
        this.reloadListener = reloadListener == null ? src -> {
        } : reloadListener;
        this.syncBus.onMessage(this::onSync);
    }

    public void warmGroups() {
        for (String name : storage.listGroups()) {
            storage.loadGroup(name).ifPresent(cache::putGroup);
        }
        if (cache.group(PermissionCalculator.DEFAULT_GROUP).isEmpty()) {
            GroupData defaults = new GroupData(PermissionCalculator.DEFAULT_GROUP);
            storage.saveGroup(defaults);
            cache.putGroup(defaults);
        }
        cache.flattened(calculator.flattenAll(cache.groupsView()));
        cache.markGroupsWarm();
    }

    @Override
    public boolean has(UUID uuid, String node) {
        return has(uuid, node, contexts.current(uuid));
    }

    @Override
    public boolean has(UUID uuid, String node, ContextSet ctx) {
        CalculatedUser user = ensureCalculated(uuid, ctx);
        return calculator.check(user.permissions(), node);
    }

    @Override
    public CompletableFuture<Boolean> hasOffline(UUID uuid, String node) {
        return hasOffline(uuid, node, contexts.current(uuid));
    }

    @Override
    public CompletableFuture<Boolean> hasOffline(UUID uuid, String node, ContextSet ctx) {
        return CompletableFuture.supplyAsync(() -> has(uuid, node, ctx), async);
    }

    @Override
    public void userAdd(UUID uuid, String node, ContextSet ctx, Duration ttl) {
        UserData user = loadOrCreateUser(uuid);
        Instant expiry = ttl == null ? null : clock.instant().plus(ttl);
        boolean value = !Wildcard.isNegated(node);
        String perm = Wildcard.normalize(node);
        ContextSet context = ctx == null ? ContextSet.empty() : ctx;
        user.nodes().removeIf(n -> n.permission().equals(perm) && n.contexts().equals(context));
        user.nodes().add(new PermissionNode(perm, value, context, expiry));
        storage.saveUser(user);
        afterUserChange(uuid);
        record("userAdd", uuid.toString(), perm + (value ? "" : " deny"));
    }

    @Override
    public void userRemove(UUID uuid, String node, ContextSet ctx) {
        UserData user = loadOrCreateUser(uuid);
        String perm = Wildcard.normalize(node);
        ContextSet context = ctx == null ? ContextSet.empty() : ctx;
        user.nodes().removeIf(n -> n.permission().equals(perm) && n.contexts().equals(context));
        storage.saveUser(user);
        afterUserChange(uuid);
        record("userRemove", uuid.toString(), perm);
    }

    @Override
    public void groupAdd(String group, String node, ContextSet ctx, Duration ttl) {
        GroupData data = loadOrCreateGroup(group);
        Instant expiry = ttl == null ? null : clock.instant().plus(ttl);
        boolean value = !Wildcard.isNegated(node);
        String perm = Wildcard.normalize(node);
        ContextSet context = ctx == null ? ContextSet.empty() : ctx;
        data.nodes().removeIf(n -> n.permission().equals(perm) && n.contexts().equals(context));
        data.nodes().add(new PermissionNode(perm, value, context, expiry));
        storage.saveGroup(data);
        cache.putGroup(data);
        afterGroupChange(data.name());
        record("groupAdd", data.name(), perm);
    }

    @Override
    public void groupRemove(String group, String node, ContextSet ctx) {
        GroupData data = loadOrCreateGroup(group);
        String perm = Wildcard.normalize(node);
        ContextSet context = ctx == null ? ContextSet.empty() : ctx;
        data.nodes().removeIf(n -> n.permission().equals(perm) && n.contexts().equals(context));
        storage.saveGroup(data);
        cache.putGroup(data);
        afterGroupChange(data.name());
        record("groupRemove", data.name(), perm);
    }

    @Override
    public void createGroup(String group) {
        String name = group.toLowerCase(Locale.ROOT);
        if (storage.loadGroup(name).isPresent()) {
            return;
        }
        GroupData data = new GroupData(name);
        storage.saveGroup(data);
        cache.putGroup(data);
        afterGroupChange(name);
        record("createGroup", name, "");
    }

    @Override
    public void deleteGroup(String group) {
        String name = group.toLowerCase(Locale.ROOT);
        if (PermissionCalculator.DEFAULT_GROUP.equals(name)) {
            throw new IllegalArgumentException("Cannot delete default group");
        }
        storage.deleteGroup(name);
        cache.invalidateGroup(name);
        afterGroupChange(name);
        record("deleteGroup", name, "");
    }

    @Override
    public void addParent(String group, String parent) {
        GroupData data = loadOrCreateGroup(group);
        String parentName = parent.toLowerCase(Locale.ROOT);
        Map<String, GroupData> all = loadAllGroups();
        if (PermissionCalculator.wouldCreateCycle(data.name(), parentName, all)) {
            throw new IllegalArgumentException("Parent would create a cycle");
        }
        data.parents().add(parentName);
        storage.saveGroup(data);
        cache.putGroup(data);
        afterGroupChange(data.name());
        record("addParent", data.name(), parentName);
    }

    @Override
    public void removeParent(String group, String parent) {
        GroupData data = loadOrCreateGroup(group);
        data.parents().remove(parent.toLowerCase(Locale.ROOT));
        storage.saveGroup(data);
        cache.putGroup(data);
        afterGroupChange(data.name());
        record("removeParent", data.name(), parent);
    }

    @Override
    public void setGroupWeight(String group, int weight) {
        GroupData data = loadOrCreateGroup(group);
        data.weight(weight);
        storage.saveGroup(data);
        cache.putGroup(data);
        afterGroupChange(data.name());
        record("setWeight", data.name(), String.valueOf(weight));
    }

    @Override
    public void addToGroup(UUID uuid, String group, Duration ttl) {
        UserData user = loadOrCreateUser(uuid);
        String name = group.toLowerCase(Locale.ROOT);
        loadOrCreateGroup(name);
        if (ttl == null) {
            user.groups().add(name);
        } else {
            user.tempMemberships().removeIf(m -> m.group().equalsIgnoreCase(name));
            user.tempMemberships().add(new UserData.TempMembership(name, clock.instant().plus(ttl)));
        }
        if (user.primaryGroup() == null) {
            user.primaryGroup(name);
        }
        storage.saveUser(user);
        afterUserChange(uuid);
        record("addToGroup", uuid.toString(), name);
    }

    @Override
    public void removeFromGroup(UUID uuid, String group) {
        UserData user = loadOrCreateUser(uuid);
        String name = group.toLowerCase(Locale.ROOT);
        user.groups().remove(name);
        user.tempMemberships().removeIf(m -> m.group().equalsIgnoreCase(name));
        if (name.equals(user.primaryGroup())) {
            user.primaryGroup(user.groups().stream().findFirst().orElse(PermissionCalculator.DEFAULT_GROUP));
        }
        storage.saveUser(user);
        afterUserChange(uuid);
        record("removeFromGroup", uuid.toString(), name);
    }

    @Override
    public void setPrimaryGroup(UUID uuid, String group) {
        UserData user = loadOrCreateUser(uuid);
        String name = group.toLowerCase(Locale.ROOT);
        loadOrCreateGroup(name);
        user.groups().add(name);
        user.primaryGroup(name);
        storage.saveUser(user);
        afterUserChange(uuid);
        record("setPrimaryGroup", uuid.toString(), name);
    }

    @Override
    public Optional<CalculatedUser> user(UUID uuid) {
        return Optional.of(ensureCalculated(uuid, contexts.current(uuid)));
    }

    @Override
    public CompletableFuture<Optional<CalculatedUser>> userAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> user(uuid), async);
    }

    @Override
    public Optional<CalculatedGroup> group(String name) {
        return loadAllGroups().values().stream()
                .filter(g -> g.name().equalsIgnoreCase(name))
                .findFirst()
                .map(g -> calculator.calculateGroup(g, loadAllGroups(), ContextSet.empty()));
    }

    @Override
    public Set<String> groupNames() {
        if (cache.groupsWarm()) {
            return cache.groupsView().keySet();
        }
        return storage.listGroups();
    }

    public void updateUserName(UUID uuid, String name) {
        UserData user = loadOrCreateUser(uuid);
        if (name != null && name.equals(user.name())) {
            return;
        }
        user.name(name);
        storage.saveUser(user);
    }

    public void invalidateUser(UUID uuid) {
        cache.invalidateUser(uuid);
    }

    public void reloadAll() {
        cache.clear();
        warmGroups();
        reloadListener.accept("local");
    }

    public void reloadNetwork() {
        reloadAll();
        syncBus.publishReloadAll();
    }

    public List<HistoryRecord> history(String targetFilter, int page) {
        int size = 15;
        int offset = Math.max(page, 1) - 1;
        return storage.listHistory(targetFilter, offset * size, size);
    }

    public LocalCache cache() {
        return cache;
    }

    public Storage storage() {
        return storage;
    }

    private void onSync(SyncMessage message) {
        switch (message.type()) {
            case USER_INVALIDATE -> {
                if (message.userId() != null) {
                    cache.invalidateUser(message.userId());
                    permissionListener.accept(new PermissionChangedEvent(message.userId(), "serversync"));
                }
            }
            case GROUP_INVALIDATE -> {
                if (message.groupName() != null) {
                    cache.invalidateGroup(message.groupName());
                    cache.invalidateUsersInGroup(message.groupName());
                    storage.loadGroup(message.groupName()).ifPresent(g -> {
                        cache.putGroup(g);
                        cache.flattened(calculator.flattenAll(cache.groupsView()));
                    });
                    groupListener.accept(new GroupChangedEvent(message.groupName(), "serversync"));
                }
            }
            case RELOAD_ALL -> {
                cache.clear();
                warmGroups();
                reloadListener.accept("serversync");
            }
            default -> {
            }
        }
    }

    private CalculatedUser ensureCalculated(UUID uuid, ContextSet ctx) {
        ContextSet context = ctx == null ? ContextSet.empty() : ctx;
        Optional<CalculatedUser> cached = cache.user(uuid, context);
        if (cached.isPresent()) {
            return cached.get();
        }
        UserData data = loadOrCreateUser(uuid);
        CalculatedUser calculated = calculator.calculateUser(data, loadAllGroups(), context, cache.flattened());
        cache.putUser(calculated, context, earliestNodeExpiry(data));
        return calculated;
    }

    private Instant earliestNodeExpiry(UserData data) {
        Instant soonest = null;
        for (PermissionNode node : data.nodes()) {
            if (node.expiry().isEmpty()) {
                continue;
            }
            Instant exp = node.expiry().get();
            if (soonest == null || exp.isBefore(soonest)) {
                soonest = exp;
            }
        }
        return soonest;
    }

    private UserData loadOrCreateUser(UUID uuid) {
        return storage.loadUser(uuid).orElseGet(() -> {
            UserData created = new UserData(uuid);
            created.groups().add(PermissionCalculator.DEFAULT_GROUP);
            created.primaryGroup(PermissionCalculator.DEFAULT_GROUP);
            storage.saveUser(created);
            return created;
        });
    }

    private GroupData loadOrCreateGroup(String group) {
        String name = group.toLowerCase(Locale.ROOT);
        Optional<GroupData> cached = cache.group(name);
        if (cached.isPresent()) {
            return cached.get();
        }
        return storage.loadGroup(name).orElseGet(() -> {
            GroupData created = new GroupData(name);
            storage.saveGroup(created);
            cache.putGroup(created);
            return created;
        });
    }

    private Map<String, GroupData> loadAllGroups() {
        if (cache.groupsWarm()) {
            return new HashMap<>(cache.groupsView());
        }
        Map<String, GroupData> map = new HashMap<>(cache.groupsView());
        for (String name : storage.listGroups()) {
            if (!map.containsKey(name)) {
                storage.loadGroup(name).ifPresent(g -> {
                    cache.putGroup(g);
                    map.put(g.name(), g);
                });
            }
        }
        cache.flattened(calculator.flattenAll(map));
        cache.markGroupsWarm();
        return map;
    }

    private void afterUserChange(UUID uuid) {
        cache.invalidateUser(uuid);
        syncBus.publishUserInvalidate(uuid);
        permissionListener.accept(new PermissionChangedEvent(uuid, "local"));
    }

    private void afterGroupChange(String group) {
        cache.invalidateUsersInGroup(group);
        cache.flattened(calculator.flattenAll(cache.groupsView()));
        syncBus.publishGroupInvalidate(group);
        groupListener.accept(new GroupChangedEvent(group, "local"));
    }

    private void record(String action, String target, String detail) {
        Actor actor = ActingContext.current();
        try {
            storage.appendHistory(new HistoryRecord(
                    clock.instant(),
                    actor.name(),
                    actor.source(),
                    action,
                    target,
                    detail == null ? "" : detail
            ));
        } catch (RuntimeException ignored) {
        }
    }
}
