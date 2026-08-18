package sh.aelion.aeperm.api;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The AepermAPI interface provides methods for managing permissions and groups for users in a permission system. It allows checking permissions, adding/removing permissions, managing groups, and retrieving user and group data.
 */
public interface AepermAPI {

    /**
     * Check if a user has a specific permission node.
     * @param uuid The UUID of the user.
     * @param node The permission node to check.
     * @return true if the user has the permission node, false otherwise.
     */
    boolean has(UUID uuid, String node);

    /**
     * Check if a user has a specific permission node with context.
     * @param uuid The UUID of the user.
     * @param node The permission node to check.
     * @param ctx The context set to consider when checking the permission.
     * @return true if the user has the permission node with the given context, false otherwise.
     */
    boolean has(UUID uuid, String node, ContextSet ctx);

    /**
     * Asynchronously check if an offline user has a specific permission node with context.
     * @param uuid The UUID of the user.
     * @param node The permission node to check.
     * @return A CompletableFuture that will complete with true if the user has the permission node with the given context, false otherwise.
     */
    CompletableFuture<Boolean> hasOffline(UUID uuid, String node);

    /**
     * Asynchronously check if an offline user has a specific permission node with context.
     * @param uuid The UUID of the user.
     * @param node The permission node to check.
     * @param ctx The context set to consider when checking the permission.
     * @return A CompletableFuture that will complete with true if the user has the permission node with the given context, false otherwise.
     */
    CompletableFuture<Boolean> hasOffline(UUID uuid, String node, ContextSet ctx);

    /**
     * Add a permission node to a user with an optional time-to-live (TTL).
     * @param uuid The UUID of the user.
     * @param node The permission node to add.
     * @param ctx The context set to associate with the permission node.
     * @param ttl The duration for which the permission node should be valid. If null, the permission will be permanent.
     */
    void userAdd(UUID uuid, String node, ContextSet ctx, Duration ttl);

    /**
     * Remove a permission node from a user.
     * @param uuid The UUID of the user.
     * @param node The permission node to remove.
     * @param ctx The context set to consider when removing the permission node. If null, the permission will be removed regardless of context.
     */
    void userRemove(UUID uuid, String node, ContextSet ctx);

    /**
     * Add a permission node to a group with an optional time-to-live (TTL).
     * @param group The name of the group.
     * @param node The permission node to add.
     * @param ctx The context set to associate with the permission node.
     * @param ttl The duration for which the permission node should be valid. If null, the permission will be permanent.
     */
    void groupAdd(String group, String node, ContextSet ctx, Duration ttl);

    /**
     * Remove a permission node from a group.
     * @param group The name of the group.
     * @param node The permission node to remove.
     * @param ctx The context set to consider when removing the permission node. If null, the permission will be removed regardless of context.
     */
    void groupRemove(String group, String node, ContextSet ctx);

    /**
     * Create a new group with the specified name.
     * @param group The name of the group to create.
     */
    void createGroup(String group);

    /**
     * Delete an existing group with the specified name.
     * @param group The name of the group to delete.
     */
    void deleteGroup(String group);

    /**
     * Add a parent group to an existing group.
     * @param group The name of the group to which the parent will be added.
     * @param parent The name of the parent group to add.
     */
    void addParent(String group, String parent);

    /**
     * Remove a parent group from an existing group.
     * @param group The name of the group from which the parent will be removed.
     * @param parent The name of the parent group to remove.
     */
    void removeParent(String group, String parent);

    /**
     * Set the weight of a group. The weight determines the priority of the group in permission calculations.
     * @param group The name of the group for which to set the weight.
     * @param weight The weight value to assign to the group. Higher values indicate higher priority.
     */
    void setGroupWeight(String group, int weight);

    /**
     * Add a user to a group with an optional time-to-live (TTL).
     * @param uuid The UUID of the user to add to the group.
     * @param group The name of the group to which the user will be added.
     * @param ttl The duration for which the user should remain in the group. If null, the user will be added permanently.
     */
    void addToGroup(UUID uuid, String group, Duration ttl);

    /**
     * Remove a user from a group.
     * @param uuid The UUID of the user to remove from the group.
     * @param group The name of the group from which the user will be removed.
     */
    void removeFromGroup(UUID uuid, String group);

    /**
     * Set the primary group of a user.
     * @param uuid The UUID of the user.
     * @param group The name of the group to set as the primary group for the user.
     */
    void setPrimaryGroup(UUID uuid, String group);

    /**
     * Get the primary group of a user.
     * @param uuid The UUID of the user.
     * @return An Optional containing the primary group name if it exists, or an empty Optional if the user has no primary group.
     */
    Optional<String> getPrimaryGroup(UUID uuid);

    /**
     * Get the calculated user data for a specific UUID.
     * @param uuid The UUID of the user.
     * @return An Optional containing the CalculatedUser if it exists, or an empty Optional if the user does not exist.
     */
    Optional<CalculatedUser> user(UUID uuid);

    /**
     * Asynchronously get the calculated user data for a specific UUID.
     * @param uuid The UUID of the user.
     * @return A CompletableFuture that will complete with an Optional containing the CalculatedUser if it exists, or an empty Optional if the user does not exist.
     */
    CompletableFuture<Optional<CalculatedUser>> userAsync(UUID uuid);

    /**
     * Get the calculated group data for a specific group name.
     * @param name The name of the group.
     * @return An Optional containing the CalculatedGroup if it exists, or an empty Optional if the group does not exist.
     */
    Optional<CalculatedGroup> group(String name);

    /**
     * Asynchronously get the calculated group data for a specific group name.
     * @return A CompletableFuture that will complete with an Optional containing the CalculatedGroup if it exists, or an empty Optional if the group does not exist.
     */
    Set<String> groupNames();

    /**
     * Get the set of group names that match a given filter string.
     * @param like The filter string to match group names against. This can be a partial name or pattern.
     * @return A set of group names that match the filter string. If no groups match, an empty set is returned.
     */
    Set<String> groupNamesFilter(String like);
    /**
     * Get the set of group names that a user belongs to.
     * @param uuid The UUID of the user.
     * @return A set of group names that the user belongs to. If the user does not belong to any groups, an empty set is returned.
     */
    Set<String> userGroups(UUID uuid);
}
