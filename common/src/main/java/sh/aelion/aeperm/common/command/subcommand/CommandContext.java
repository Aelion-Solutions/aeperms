package sh.aelion.aeperm.common.command.subcommand;

import sh.aelion.aeperm.common.model.UserData;
import sh.aelion.aeperm.common.service.PermissionService;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CommandContext {

    private final PermissionService permissions;
    private final Function<String, Optional<UUID>> nameResolver;
    private final Supplier<Collection<String>> onlineNames;
    private final CommandMeta meta;

    public CommandContext(
            PermissionService permissions,
            Function<String, Optional<UUID>> nameResolver,
            Supplier<Collection<String>> onlineNames,
            CommandMeta meta
    ) {
        this.permissions = permissions;
        this.nameResolver = nameResolver;
        this.onlineNames = onlineNames == null ? List::of : onlineNames;
        this.meta = meta == null ? CommandMeta.of("unknown", false) : meta;
    }

    public PermissionService permissions() {
        return permissions;
    }

    public CommandMeta meta() {
        return meta;
    }

    public Collection<String> onlineNames() {
        return onlineNames.get();
    }

    public Optional<UUID> resolveTarget(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            Optional<UUID> online = nameResolver.apply(raw);
            if (online.isPresent()) {
                return online;
            }
            return permissions.storage().findUserByName(raw).map(UserData::uuid);
        }
    }
}
