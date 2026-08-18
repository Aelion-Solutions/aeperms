package net.beteax.aeperm.api;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AepermAPI {

    boolean has(UUID uuid, String node);

    boolean has(UUID uuid, String node, ContextSet ctx);

    CompletableFuture<Boolean> hasOffline(UUID uuid, String node);

    CompletableFuture<Boolean> hasOffline(UUID uuid, String node, ContextSet ctx);

    void userAdd(UUID uuid, String node, ContextSet ctx, Duration ttl);

    void userRemove(UUID uuid, String node, ContextSet ctx);

    void groupAdd(String group, String node, ContextSet ctx, Duration ttl);

    void groupRemove(String group, String node, ContextSet ctx);

    void createGroup(String group);

    void deleteGroup(String group);

    void addParent(String group, String parent);

    void removeParent(String group, String parent);

    void setGroupWeight(String group, int weight);

    void addToGroup(UUID uuid, String group, Duration ttl);

    void removeFromGroup(UUID uuid, String group);

    void setPrimaryGroup(UUID uuid, String group);

    Optional<CalculatedUser> user(UUID uuid);

    CompletableFuture<Optional<CalculatedUser>> userAsync(UUID uuid);

    Optional<CalculatedGroup> group(String name);

    Set<String> groupNames();
}
