package net.beteax.aeperm.common.storage;

import net.beteax.aeperm.common.history.HistoryRecord;
import net.beteax.aeperm.common.model.GroupData;
import net.beteax.aeperm.common.model.UserData;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface Storage extends AutoCloseable {

    void init();

    Optional<UserData> loadUser(UUID uuid);

    Optional<UserData> findUserByName(String name);

    void saveUser(UserData user);

    Optional<GroupData> loadGroup(String name);

    void saveGroup(GroupData group);

    void deleteGroup(String name);

    Set<String> listGroups();

    Set<String> listUserNames();

    void appendHistory(HistoryRecord record);

    List<HistoryRecord> listHistory(String targetFilter, int offset, int limit);

    @Override
    void close();
}
