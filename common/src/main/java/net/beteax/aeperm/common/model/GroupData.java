package net.beteax.aeperm.common.model;

import net.beteax.aeperm.api.PermissionNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GroupData {

    private final String name;
    private int weight;
    private final Set<String> parents = new LinkedHashSet<>();
    private final List<PermissionNode> nodes = new ArrayList<>();

    public GroupData(String name) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase();
    }

    public String name() {
        return name;
    }

    public int weight() {
        return weight;
    }

    public void weight(int weight) {
        this.weight = weight;
    }

    public Set<String> parents() {
        return parents;
    }

    public List<PermissionNode> nodes() {
        return nodes;
    }
}
