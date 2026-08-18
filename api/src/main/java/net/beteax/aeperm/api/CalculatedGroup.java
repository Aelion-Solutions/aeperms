package net.beteax.aeperm.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CalculatedGroup {

    private final String name;
    private final int weight;
    private final Set<String> parents;
    private final Map<String, Boolean> permissions;

    public CalculatedGroup(String name, int weight, Set<String> parents, Map<String, Boolean> permissions) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase();
        this.weight = weight;
        this.parents = Collections.unmodifiableSet(parents);
        this.permissions = Collections.unmodifiableMap(permissions);
    }

    public String name() {
        return name;
    }

    public int weight() {
        return weight;
    }

    public Set<String> parents() {
        return parents;
    }

    public Map<String, Boolean> permissions() {
        return permissions;
    }
}
