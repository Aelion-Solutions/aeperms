package sh.aelion.aeperm.common.storage;

import sh.aelion.aeperm.common.history.HistoryRecord;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;

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

    List<String> listUserNames(String prefix, int limit);

    void appendHistory(HistoryRecord record);

    List<HistoryRecord> listHistory(String targetFilter, int offset, int limit);

    int countHistory(String targetFilter);

    @Override
    void close();
}
