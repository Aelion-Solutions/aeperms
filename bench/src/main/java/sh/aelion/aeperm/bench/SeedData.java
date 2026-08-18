package sh.aelion.aeperm.bench;

import sh.aelion.aeperm.api.AepermAPI;
import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.api.ContextSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SeedData {

    public static final int DEFAULT_NODES = 500;
    public static final int MAX_NODES = 10_000;
    public static final int GROUP_NODE_COUNT = 50;

    public static final String GROUP_FILTER = "aeperm-bench";
    public static final String GROUP_DEFAULT = "aeperm-bench-default";
    public static final String GROUP_MEMBER = "aeperm-bench-member";
    public static final String GROUP_VIP = "aeperm-bench-vip";
    public static final String GROUP_STAFF = "aeperm-bench-staff";

    public static final String NODE_PREFIX = "aeperm.bench.";
    public static final String USER_NODE_PREFIX = "aeperm.bench.n.";
    public static final String WILD_NODE = "aeperm.bench.wild.*";
    public static final String WILD_CHECK = "aeperm.bench.wild.foo";
    public static final String DENY_NODE = "aeperm.bench.denied";
    public static final String WORLD_NODE = "aeperm.bench.world";
    public static final String MISS_NODE = "aeperm.bench.missing.nope";
    public static final String RECALC_NODE = "aeperm.bench.recalc";

    private static final String[] GROUPS = {GROUP_DEFAULT, GROUP_MEMBER, GROUP_VIP, GROUP_STAFF};
    private static final int[] WEIGHTS = {1, 10, 50, 100};

    private final AepermAPI api;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public SeedData(AepermAPI api) {
        this.api = api;
    }

    public record Snapshot(String primaryGroup, int userNodes, String world) {
    }

    public record Report(int userNodes, int groups, int effectiveNodes) {
    }

    public static String exactNode() {
        return USER_NODE_PREFIX + "0";
    }

    public Report seed(UUID uuid, int userNodes, String world) {
        String previousPrimary = api.getPrimaryGroup(uuid).orElse(null);
        snapshots.put(uuid, new Snapshot(previousPrimary, userNodes, world));

        for (int i = 0; i < GROUPS.length; i++) {
            api.createGroup(GROUPS[i]);
            api.setGroupWeight(GROUPS[i], WEIGHTS[i]);
            for (int n = 0; n < GROUP_NODE_COUNT; n++) {
                api.groupAdd(GROUPS[i], NODE_PREFIX + "g." + shortName(GROUPS[i]) + "." + n, ContextSet.empty(), null);
            }
        }
        api.addParent(GROUP_MEMBER, GROUP_DEFAULT);
        api.addParent(GROUP_VIP, GROUP_MEMBER);
        api.addParent(GROUP_STAFF, GROUP_VIP);
        api.groupAdd(GROUP_STAFF, WILD_NODE, ContextSet.empty(), null);
        api.groupAdd(GROUP_STAFF, "-" + DENY_NODE, ContextSet.empty(), null);

        api.addToGroup(uuid, GROUP_STAFF, null);

        for (int i = 0; i < userNodes; i++) {
            api.userAdd(uuid, USER_NODE_PREFIX + i, ContextSet.empty(), null);
        }
        api.userAdd(uuid, WORLD_NODE, ContextSet.builder().world(world).build(), null);

        CalculatedUser user = api.user(uuid).orElseThrow();
        return new Report(userNodes, user.groups().size(), user.permissions().size());
    }

    public Report cleanup(UUID uuid, String worldName) {
        Snapshot snapshot = snapshots.remove(uuid);
        String world = snapshot == null ? worldName : snapshot.world();

        for (String group : api.userGroups(uuid)) {
            if (group.contains(GROUP_FILTER)) {
                api.removeFromGroup(uuid, group);
            }
        }

        List<String> userNodes = new ArrayList<>();
        Optional<CalculatedUser> calculated = api.user(uuid);
        if (calculated.isPresent()) {
            for (String node : calculated.get().permissions().keySet()) {
                if (node.startsWith(NODE_PREFIX)) {
                    userNodes.add(node);
                }
            }
        }
        if (snapshot != null) {
            for (int i = 0; i < snapshot.userNodes(); i++) {
                userNodes.add(USER_NODE_PREFIX + i);
            }
        }
        userNodes.add(WORLD_NODE);
        userNodes.add(RECALC_NODE);
        userNodes.add(DENY_NODE);
        userNodes.add(WILD_NODE);
        ContextSet worldCtx = ContextSet.builder().world(world).build();
        for (String node : userNodes) {
            api.userRemove(uuid, node, ContextSet.empty());
            api.userRemove(uuid, node, worldCtx);
        }

        if (snapshot != null && snapshot.primaryGroup() != null && !snapshot.primaryGroup().contains(GROUP_FILTER)) {
            api.setPrimaryGroup(uuid, snapshot.primaryGroup());
        }

        Set<String> benchGroups = api.groupNamesFilter(GROUP_FILTER);
        List<String> ordered = new ArrayList<>(List.of(GROUP_STAFF, GROUP_VIP, GROUP_MEMBER, GROUP_DEFAULT));
        for (String extra : benchGroups) {
            if (!ordered.contains(extra)) {
                ordered.add(extra);
            }
        }
        int deleted = 0;
        for (String group : ordered) {
            if (benchGroups.contains(group)) {
                api.deleteGroup(group);
                deleted++;
            }
        }

        CalculatedUser user = api.user(uuid).orElseThrow();
        return new Report(snapshot == null ? userNodes.size() : snapshot.userNodes(), deleted, user.permissions().size());
    }

    private static String shortName(String group) {
        int idx = group.lastIndexOf('-');
        return idx < 0 ? group : group.substring(idx + 1);
    }
}
