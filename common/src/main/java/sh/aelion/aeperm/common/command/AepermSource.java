package sh.aelion.aeperm.common.command;

import net.kyori.adventure.audience.Audience;

import java.util.Objects;
import java.util.function.Predicate;

public final class AepermSource {

    private final Audience audience;
    private final Predicate<String> permissions;
    private final String actorName;

    public AepermSource(Audience audience, Predicate<String> permissions) {
        this(audience, permissions, "console");
    }

    public AepermSource(Audience audience, Predicate<String> permissions, String actorName) {
        this.audience = Objects.requireNonNull(audience, "audience");
        this.permissions = permissions == null ? ignored -> true : permissions;
        this.actorName = actorName == null || actorName.isBlank() ? "console" : actorName;
    }

    public Audience audience() {
        return audience;
    }

    public String actorName() {
        return actorName;
    }

    public boolean hasPermission(String permission) {
        return permission == null || permission.isBlank() || permissions.test(permission);
    }
}
