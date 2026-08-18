package net.beteax.aeperm.common.cache;

import net.beteax.aeperm.api.CalculatedUser;
import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.api.PermissionNode;
import net.beteax.aeperm.common.model.GroupData;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalCache {

    private final Clock clock;
    private final Duration userTtl;
    private final Map<UserKey, CacheEntry<CalculatedUser>> users = new ConcurrentHashMap<>();
    private final Map<String, GroupData> groups = new ConcurrentHashMap<>();
    private final Map<String, List<PermissionNode>> flattened = new ConcurrentHashMap<>();
    private final AtomicBoolean groupsWarm = new AtomicBoolean();

    public LocalCache(Clock clock, Duration userTtl) {
        this.clock = clock;
        this.userTtl = userTtl;
    }

    public Optional<CalculatedUser> user(UUID uuid, ContextSet ctx) {
        UserKey key = key(uuid, ctx);
        CacheEntry<CalculatedUser> entry = users.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(clock.instant())) {
            users.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public Optional<CalculatedUser> userAny(UUID uuid) {
        Instant now = clock.instant();
        for (Map.Entry<UserKey, CacheEntry<CalculatedUser>> entry : users.entrySet()) {
            if (!entry.getKey().uuid().equals(uuid)) {
                continue;
            }
            if (entry.getValue().expiresAt().isBefore(now)) {
                users.remove(entry.getKey(), entry.getValue());
                continue;
            }
            return Optional.of(entry.getValue().value());
        }
        return Optional.empty();
    }

    public void putUser(CalculatedUser user, ContextSet ctx, Instant expiresAt) {
        Instant ttlCap = clock.instant().plus(userTtl);
        Instant cap = expiresAt == null || expiresAt.isAfter(ttlCap) ? ttlCap : expiresAt;
        users.put(key(user.uuid(), ctx), new CacheEntry<>(user, cap));
    }

    public void putUser(CalculatedUser user) {
        putUser(user, ContextSet.empty(), clock.instant().plus(userTtl));
    }

    public void invalidateUser(UUID uuid) {
        users.keySet().removeIf(key -> key.uuid().equals(uuid));
    }

    public void invalidateUsersInGroup(String group) {
        String needle = group.toLowerCase();
        users.entrySet().removeIf(e -> e.getValue().value().groups().contains(needle));
    }

    public void putGroup(GroupData group) {
        groups.put(group.name(), group);
    }

    public Optional<GroupData> group(String name) {
        return Optional.ofNullable(groups.get(name.toLowerCase()));
    }

    public void invalidateGroup(String name) {
        groups.remove(name.toLowerCase());
        flattened.remove(name.toLowerCase());
    }

    public void clear() {
        users.clear();
        groups.clear();
        flattened.clear();
        groupsWarm.set(false);
    }

    public int userCount() {
        return users.size();
    }

    public int groupCount() {
        return groups.size();
    }

    public Map<String, GroupData> groupsView() {
        return Map.copyOf(groups);
    }

    public boolean groupsWarm() {
        return groupsWarm.get();
    }

    public void markGroupsWarm() {
        groupsWarm.set(true);
    }

    public void flattened(Map<String, List<PermissionNode>> snapshots) {
        flattened.clear();
        flattened.putAll(snapshots);
    }

    public Map<String, List<PermissionNode>> flattened() {
        return new HashMap<>(flattened);
    }

    private static UserKey key(UUID uuid, ContextSet ctx) {
        return new UserKey(uuid, ctx == null ? ContextSet.empty() : ctx);
    }

    private record UserKey(UUID uuid, ContextSet ctx) {
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }
}
