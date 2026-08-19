package sh.aelion.aeperm.common.calc;

import sh.aelion.aeperm.api.CalculatedGroup;
import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.api.PermissionNode;
import sh.aelion.aeperm.api.Wildcard;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PermissionCalculator {

    public static final String DEFAULT_GROUP = "default";

    private final Clock clock;

    public PermissionCalculator(Clock clock) {
        this.clock = clock;
    }

    public CalculatedUser calculateUser(UserData user, Map<String, GroupData> groups, ContextSet active) {
        return calculateUser(user, groups, active, flattenAll(groups));
    }

    public CalculatedUser calculateUser(
            UserData user,
            Map<String, GroupData> groups,
            ContextSet active,
            Map<String, List<PermissionNode>> flattened
    ) {
        Instant now = clock.instant();
        Set<String> effectiveGroups = resolveGroups(user, now);
        if (effectiveGroups.isEmpty()) {
            effectiveGroups = new LinkedHashSet<>();
            effectiveGroups.add(DEFAULT_GROUP);
        }

        Map<String, Boolean> accumulated = new LinkedHashMap<>();
        List<GroupData> ordered = orderGroups(effectiveGroups, groups);
        Map<String, List<PermissionNode>> snapshots = flattened == null ? flattenAll(groups) : flattened;
        for (GroupData group : ordered) {
            List<PermissionNode> inherited = snapshots.getOrDefault(group.name(), List.of());
            mergeNodes(accumulated, filterNodes(inherited, active, now));
        }
        mergeNodes(accumulated, filterNodes(user.nodes(), active, now));

        String primary = user.primaryGroup();
        if (primary == null && !effectiveGroups.isEmpty()) {
            primary = effectiveGroups.iterator().next();
        }

        return new CalculatedUser(user.uuid(), user.name(), primary, effectiveGroups, accumulated);
    }

    public Map<String, List<PermissionNode>> flattenAll(Map<String, GroupData> groups) {
        Map<String, List<PermissionNode>> out = new LinkedHashMap<>();
        for (GroupData group : groups.values()) {
            out.put(group.name(), flattenGroup(group, groups, new HashSet<>()));
        }
        return out;
    }

    private List<PermissionNode> flattenGroup(GroupData group, Map<String, GroupData> groups, Set<String> visiting) {
        List<PermissionNode> result = new ArrayList<>();
        if (!visiting.add(group.name())) {
            return result;
        }
        List<GroupData> parents = new ArrayList<>();
        for (String parentName : group.parents()) {
            GroupData parent = groups.get(parentName.toLowerCase());
            if (parent != null) {
                parents.add(parent);
            }
        }
        parents.sort(Comparator.comparingInt(GroupData::weight));
        for (GroupData parent : parents) {
            result.addAll(flattenGroup(parent, groups, visiting));
        }
        result.addAll(group.nodes());
        visiting.remove(group.name());
        return result;
    }

    public CalculatedGroup calculateGroup(GroupData group, Map<String, GroupData> groups, ContextSet active) {
        Instant now = clock.instant();
        Map<String, Boolean> perms = new LinkedHashMap<>();
        mergeNodes(perms, filterNodes(flattenGroup(group, groups, new HashSet<>()), active, now));
        return new CalculatedGroup(group.name(), group.weight(), new LinkedHashSet<>(group.parents()), perms);
    }

    public boolean check(Map<String, Boolean> permissions, String node) {
        String normalized = Wildcard.normalize(node);
        Boolean exact = permissions.get(normalized);
        if (exact != null) {
            return exact;
        }
        return Wildcard.match(permissions, normalized);
    }

    private Set<String> resolveGroups(UserData user, Instant now) {
        Set<String> result = new LinkedHashSet<>();
        for (String group : user.groups()) {
            result.add(group.toLowerCase());
        }
        for (UserData.TempMembership membership : user.tempMemberships()) {
            if (membership.expiry() == null || membership.expiry().isAfter(now)) {
                result.add(membership.group().toLowerCase());
            }
        }
        return result;
    }

    private List<GroupData> orderGroups(Set<String> names, Map<String, GroupData> groups) {
        List<GroupData> list = new ArrayList<>();
        for (String name : names) {
            GroupData data = groups.get(name.toLowerCase());
            if (data != null) {
                list.add(data);
            } else if (DEFAULT_GROUP.equals(name)) {
                GroupData fallback = new GroupData(DEFAULT_GROUP);
                list.add(fallback);
            }
        }
        list.sort(Comparator.comparingInt(GroupData::weight));
        return list;
    }

    private List<PermissionNode> filterNodes(List<PermissionNode> nodes, ContextSet active, Instant now) {
        List<PermissionNode> filtered = new ArrayList<>();
        for (PermissionNode node : nodes) {
            if (node.expired(now)) {
                continue;
            }
            if (!node.contexts().isEmpty() && !active.matches(node.contexts())) {
                continue;
            }
            filtered.add(node);
        }
        return filtered;
    }

    private void mergeNodes(Map<String, Boolean> target, List<PermissionNode> nodes) {
        for (PermissionNode node : nodes) {
            target.put(node.permission(), node.value());
        }
    }

    /**
     * Groups whose ancestor chain includes {@code changed}, including {@code changed} itself.
     * Parent names match even if that parent row is missing from {@code groups}.
     */
    public static Set<String> groupsInheriting(String changed, Map<String, GroupData> groups) {
        String needle = changed.toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        out.add(needle);
        if (groups == null || groups.isEmpty()) {
            return out;
        }
        for (GroupData group : groups.values()) {
            if (inherits(group, needle, groups, new HashSet<>())) {
                out.add(group.name());
            }
        }
        return out;
    }

    public static boolean userInGroups(Set<String> userGroups, Set<String> groupNames) {
        if (userGroups == null || userGroups.isEmpty() || groupNames == null || groupNames.isEmpty()) {
            return false;
        }
        for (String name : userGroups) {
            if (groupNames.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean wouldCreateCycle(String group, String parent, Map<String, GroupData> groups) {
        if (group.equalsIgnoreCase(parent)) {
            return true;
        }
        Set<String> visiting = new HashSet<>();
        return reaches(parent.toLowerCase(), group.toLowerCase(), groups, visiting);
    }

    private static boolean inherits(GroupData group, String ancestor, Map<String, GroupData> groups, Set<String> visiting) {
        if (!visiting.add(group.name())) {
            return false;
        }
        if (group.name().equals(ancestor)) {
            return true;
        }
        for (String parent : group.parents()) {
            String parentName = parent.toLowerCase(Locale.ROOT);
            if (parentName.equals(ancestor)) {
                return true;
            }
            GroupData parentGroup = groups.get(parentName);
            if (parentGroup != null && inherits(parentGroup, ancestor, groups, visiting)) {
                return true;
            }
        }
        return false;
    }

    private static boolean reaches(String current, String target, Map<String, GroupData> groups, Set<String> visiting) {
        if (!visiting.add(current)) {
            return false;
        }
        if (current.equals(target)) {
            return true;
        }
        GroupData data = groups.get(current);
        if (data == null) {
            return false;
        }
        for (String parent : data.parents()) {
            if (reaches(parent.toLowerCase(), target, groups, visiting)) {
                return true;
            }
        }
        return false;
    }
}
