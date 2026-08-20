package sh.aelion.aeperm.paper;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import sh.aelion.aeperm.api.Wildcard;

import java.util.Map;
import java.util.Optional;

/**
 * Intercepts Bukkit {@code hasPermission} so {@code *} and {@code minecraft.*} resolve
 * the way the AePerm calculator does. Superperms only stores exact keys.
 */
final class AepermPermissible extends PermissibleBase {

    private final Player player;
    private final AepermPlugin plugin;
    private PermissibleBase oldPermissible;

    AepermPermissible(Player player, AepermPlugin plugin) {
        super(player);
        this.player = player;
        this.plugin = plugin;
    }

    void oldPermissible(PermissibleBase oldPermissible) {
        this.oldPermissible = oldPermissible;
    }

    PermissibleBase oldPermissible() {
        return oldPermissible;
    }

    @Override
    public boolean isPermissionSet(String name) {
        return lookup(name).isPresent() || super.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return isPermissionSet(perm.getName());
    }

    @Override
    public boolean hasPermission(String name) {
        return lookup(name).orElseGet(() -> super.hasPermission(name));
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return lookup(perm.getName()).orElseGet(() -> super.hasPermission(perm));
    }

    private Optional<Boolean> lookup(String name) {
        Map<String, Boolean> nodes = plugin.cachedPermissions(player.getUniqueId());
        if (nodes == null) {
            return Optional.empty();
        }
        return Wildcard.lookup(nodes, name);
    }
}
