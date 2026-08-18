package sh.aelion.aeperm.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CalculatedGroup(String name, int weight, Set<String> parents, Map<String, Boolean> permissions) {

    public CalculatedGroup(String name, int weight, Set<String> parents, Map<String, Boolean> permissions) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase();
        this.weight = weight;
        this.parents = Collections.unmodifiableSet(parents);
        this.permissions = Collections.unmodifiableMap(permissions);
    }
}
