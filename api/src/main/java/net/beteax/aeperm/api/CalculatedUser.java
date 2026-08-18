package net.beteax.aeperm.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CalculatedUser {

    private final UUID uuid;
    private final String name;
    private final String primaryGroup;
    private final Set<String> groups;
    private final Map<String, Boolean> permissions;

    public CalculatedUser(
            UUID uuid,
            String name,
            String primaryGroup,
            Set<String> groups,
            Map<String, Boolean> permissions
    ) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = name;
        this.primaryGroup = primaryGroup;
        this.groups = Collections.unmodifiableSet(groups);
        this.permissions = Collections.unmodifiableMap(permissions);
    }

    public UUID uuid() {
        return uuid;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> primaryGroup() {
        return Optional.ofNullable(primaryGroup);
    }

    public Set<String> groups() {
        return groups;
    }

    public Map<String, Boolean> permissions() {
        return permissions;
    }

    public boolean has(String node) {
        String normalized = Wildcard.normalize(node);
        Boolean exact = permissions.get(normalized);
        if (exact != null) {
            return exact;
        }
        return Wildcard.match(permissions, normalized);
    }
}
