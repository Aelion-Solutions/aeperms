package sh.aelion.aeperm.common.storage;

import sh.aelion.aeperm.common.calc.PermissionCalculator;
import sh.aelion.aeperm.common.history.HistoryRecord;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class MemoryStorage implements Storage {

    private final Map<UUID, UserData> users = new ConcurrentHashMap<>();
    private final Map<String, GroupData> groups = new ConcurrentHashMap<>();
    private final List<HistoryRecord> history = new CopyOnWriteArrayList<>();

    @Override
    public void init() {
        groups.computeIfAbsent(PermissionCalculator.DEFAULT_GROUP, GroupData::new);
    }

    @Override
    public Optional<UserData> loadUser(UUID uuid) {
        UserData data = users.get(uuid);
        return data == null ? Optional.empty() : Optional.of(copyUser(data));
    }

    @Override
    public Optional<UserData> findUserByName(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return users.values().stream()
                .filter(u -> u.name() != null && u.name().equalsIgnoreCase(needle))
                .findFirst()
                .map(this::copyUser);
    }

    @Override
    public void saveUser(UserData user) {
        users.put(user.uuid(), copyUser(user));
    }

    @Override
    public Optional<GroupData> loadGroup(String name) {
        GroupData data = groups.get(name.toLowerCase(Locale.ROOT));
        return data == null ? Optional.empty() : Optional.of(copyGroup(data));
    }

    @Override
    public void saveGroup(GroupData group) {
        groups.put(group.name(), copyGroup(group));
    }

    @Override
    public void deleteGroup(String name) {
        groups.remove(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public Set<String> listGroups() {
        return Set.copyOf(groups.keySet());
    }

    @Override
    public Set<String> listUserNames() {
        return users.values().stream()
                .map(UserData::name)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<String> listUserNames(String prefix, int limit) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return users.values().stream()
                .map(UserData::name)
                .filter(n -> n != null && !n.isBlank())
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(needle))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .distinct()
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public void appendHistory(HistoryRecord record) {
        history.add(record);
    }

    @Override
    public List<HistoryRecord> listHistory(String targetFilter, int offset, int limit) {
        String needle = targetFilter == null ? "" : targetFilter.toLowerCase(Locale.ROOT);
        List<HistoryRecord> filtered = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryRecord record = history.get(i);
            if (needle.isBlank() || record.target().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(record);
            }
        }
        int from = Math.min(offset, filtered.size());
        int to = Math.min(from + Math.max(limit, 0), filtered.size());
        return List.copyOf(filtered.subList(from, to));
    }

    @Override
    public void close() {
        users.clear();
        groups.clear();
        history.clear();
    }

    private UserData copyUser(UserData source) {
        UserData copy = new UserData(source.uuid());
        copy.name(source.name());
        copy.primaryGroup(source.primaryGroup());
        copy.groups().addAll(source.groups());
        copy.nodes().addAll(source.nodes());
        for (UserData.TempMembership membership : source.tempMemberships()) {
            copy.tempMemberships().add(new UserData.TempMembership(membership.group(), membership.expiry()));
        }
        return copy;
    }

    private GroupData copyGroup(GroupData source) {
        GroupData copy = new GroupData(source.name());
        copy.weight(source.weight());
        copy.parents().addAll(source.parents());
        copy.nodes().addAll(source.nodes());
        return copy;
    }
}
