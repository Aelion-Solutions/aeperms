package sh.aelion.aeperm.common.model;

import lombok.Getter;
import lombok.Setter;
import sh.aelion.aeperm.api.PermissionNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
public final class GroupData {

    private final String name;
    @Setter
    private int weight;
    private final Set<String> parents = new LinkedHashSet<>();
    private final List<PermissionNode> nodes = new ArrayList<>();

    public GroupData(String name) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase();
    }
}
