package net.beteax.aeperm.common.storage;

import net.beteax.aeperm.api.PermissionNode;
import net.beteax.aeperm.common.model.GroupData;
import net.beteax.aeperm.common.model.UserData;
import org.junit.jupiter.api.Test;

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
        assertThat(storage.listUserNames()).contains("Alex");

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
