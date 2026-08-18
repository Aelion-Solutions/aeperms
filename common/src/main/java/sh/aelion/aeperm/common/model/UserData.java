package sh.aelion.aeperm.common.model;

import lombok.Getter;
import lombok.Setter;
import sh.aelion.aeperm.api.PermissionNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
public final class UserData {

    private final UUID uuid;
    @Setter
    private String name;
    private String primaryGroup;
    private final Set<String> groups = new LinkedHashSet<>();
    private final List<PermissionNode> nodes = new ArrayList<>();
    private final List<TempMembership> tempMemberships = new ArrayList<>();

    public UserData(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    public void primaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup == null ? null : primaryGroup.toLowerCase();
    }

    public record TempMembership(String group, Instant expiry) {
    }
}
