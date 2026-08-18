package net.beteax.aeperm.common.model;

import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.api.PermissionNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class UserData {

    private final UUID uuid;
    private String name;
    private String primaryGroup;
    private final Set<String> groups = new LinkedHashSet<>();
    private final List<PermissionNode> nodes = new ArrayList<>();
    private final List<TempMembership> tempMemberships = new ArrayList<>();

    public UserData(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String primaryGroup() {
        return primaryGroup;
    }

    public void primaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup == null ? null : primaryGroup.toLowerCase();
    }

    public Set<String> groups() {
        return groups;
    }

    public List<PermissionNode> nodes() {
        return nodes;
    }

    public List<TempMembership> tempMemberships() {
        return tempMemberships;
    }

    public record TempMembership(String group, Instant expiry) {
    }
}
