package sh.aelion.aeperm.common.storage;

import sh.aelion.aeperm.api.PermissionNode;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStorageTest {

    @Test
    void crud() {
        MemoryStorage storage = new MemoryStorage();
        storage.init();
        assertThat(storage.listGroups()).contains("default");

        UUID uuid = UUID.randomUUID();
        UserData user = new UserData(uuid);
        user.name("Alex");
        user.nodes().add(PermissionNode.allow("test.node"));
        storage.saveUser(user);

        assertThat(storage.loadUser(uuid)).isPresent();
        assertThat(storage.findUserByName("alex")).isPresent();
        assertThat(storage.listUserNames("Al", 10)).containsExactly("Alex");
        assertThat(storage.listUserNames("", 10)).isEmpty();
        assertThat(storage.listUserNames("zz", 10)).isEmpty();

        for (int i = 0; i < 1000; i++) {
            UserData bulk = new UserData(UUID.randomUUID());
            bulk.name(String.format("User%04d", i));
            storage.saveUser(bulk);
        }
        List<String> prefix = storage.listUserNames("User00", 50);
        assertThat(prefix).hasSize(50);
        assertThat(prefix).allMatch(n -> n.startsWith("User00"));
        assertThat(storage.listUserNames("User", 50)).hasSize(50);

        GroupData group = new GroupData("staff");
        group.weight(5);
        group.parents().add("default");
        storage.saveGroup(group);
        assertThat(storage.loadGroup("staff")).isPresent();
        storage.deleteGroup("staff");
        assertThat(storage.loadGroup("staff")).isEmpty();
        storage.close();
    }
}
