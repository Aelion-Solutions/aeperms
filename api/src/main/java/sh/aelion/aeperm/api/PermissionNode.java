package sh.aelion.aeperm.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PermissionNode {

    private final String permission;
    private final boolean value;
    private final ContextSet contexts;
    private final Instant expiry;

    public PermissionNode(String permission, boolean value, ContextSet contexts, Instant expiry) {
        this.permission = Objects.requireNonNull(permission, "permission").toLowerCase();
        this.value = value;
        this.contexts = contexts == null ? ContextSet.empty() : contexts;
        this.expiry = expiry;
    }

    public static PermissionNode allow(String permission) {
        return new PermissionNode(permission, true, ContextSet.empty(), null);
    }

    public static PermissionNode deny(String permission) {
        return new PermissionNode(permission, false, ContextSet.empty(), null);
    }

    public String permission() {
        return permission;
    }

    public boolean value() {
        return value;
    }

    public ContextSet contexts() {
        return contexts;
    }

    public Optional<Instant> expiry() {
        return Optional.ofNullable(expiry);
    }

    public boolean expired(Instant now) {
        return expiry != null && !expiry.isAfter(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PermissionNode that)) {
            return false;
        }
        return value == that.value
                && permission.equals(that.permission)
                && contexts.equals(that.contexts)
                && Objects.equals(expiry, that.expiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permission, value, contexts, expiry);
    }
}
