package sh.aelion.aeperm.common.storage;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.api.PermissionNode;
import sh.aelion.aeperm.common.config.AepermConfig;
import sh.aelion.aeperm.common.history.HistoryRecord;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SqlStoragePostgresTest {

    @Container
    static final PostgreSQLContainer<?> FRESH = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aeperm")
            .withUsername("aeperm")
            .withPassword("aeperm");

    @Container
    static final PostgreSQLContainer<?> LEGACY = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aeperm")
            .withUsername("aeperm")
            .withPassword("aeperm");

    @Test
    void roundTripUserGroupHistory() {
        SqlStorageSupport.assertRoundTrip(SqlStorageSupport.storage(FRESH));
    }

    @Test
    void migratesLegacyJson() throws Exception {
        SqlStorageSupport.assertLegacyJson(LEGACY, true);
    }
}

@Testcontainers
class SqlStorageMariaDbTest {

    @Container
    static final MariaDBContainer<?> FRESH = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("aeperm")
            .withUsername("aeperm")
            .withPassword("aeperm");

    @Container
    static final MariaDBContainer<?> LEGACY = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("aeperm")
            .withUsername("aeperm")
            .withPassword("aeperm");

    @Test
    void roundTripUserGroupHistory() {
        SqlStorageSupport.assertRoundTrip(SqlStorageSupport.storage(FRESH));
    }

    @Test
    void migratesLegacyJson() throws Exception {
        SqlStorageSupport.assertLegacyJson(LEGACY, false);
    }
}

final class SqlStorageSupport {

    private SqlStorageSupport() {
    }

    static SqlStorage storage(JdbcDatabaseContainer<?> container) {
        SqlStorage storage = new SqlStorage(AepermConfig.fromMap(Map.of(
                "storage", Map.of(
                        "url", container.getJdbcUrl(),
                        "user", container.getUsername(),
                        "password", container.getPassword()
                )
        )).storage());
        storage.init();
        return storage;
    }

    static void assertRoundTrip(SqlStorage storage) {
        try {
            storage.sessionFactory().getSchemaManager().validateMappedObjects();

            UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000042");
            Instant expiry = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

            GroupData staff = new GroupData("staff");
            staff.weight(10);
            staff.parents().add("default");
            staff.nodes().add(new PermissionNode(
                    "staff.use",
                    true,
                    ContextSet.builder().server("lobby").build(),
                    null
            ));
            storage.saveGroup(staff);

            UserData user = new UserData(uuid);
            user.name("Alex");
            user.primaryGroup("staff");
            user.groups().add("default");
            user.groups().add("staff");
            user.tempMemberships().add(new UserData.TempMembership("staff", expiry));
            user.nodes().add(PermissionNode.allow("user.kit"));
            user.nodes().add(new PermissionNode(
                    "essentials.fly",
                    false,
                    ContextSet.builder().world("world").build(),
                    expiry
            ));
            storage.saveUser(user);

            UserData loaded = storage.loadUser(uuid).orElseThrow();
            assertThat(loaded.name()).isEqualTo("Alex");
            assertThat(loaded.primaryGroup()).isEqualTo("staff");
            assertThat(loaded.groups()).containsExactlyInAnyOrder("default", "staff");
            assertThat(loaded.tempMemberships()).hasSize(1);
            assertThat(loaded.tempMemberships().getFirst().group()).isEqualTo("staff");
            assertThat(loaded.tempMemberships().getFirst().expiry()).isEqualTo(expiry);
            assertThat(loaded.nodes()).hasSize(2);
            assertThat(storage.findUserByName("alex")).isPresent();
            assertThat(storage.listUserNames("Al", 10)).contains("Alex");
            assertThat(storage.listUserNames("", 10)).isEmpty();
            assertThat(storage.listGroups()).contains("default", "staff");

            GroupData loadedGroup = storage.loadGroup("staff").orElseThrow();
            assertThat(loadedGroup.weight()).isEqualTo(10);
            assertThat(loadedGroup.parents()).containsExactly("default");
            assertThat(loadedGroup.nodes()).hasSize(1);
            assertThat(loadedGroup.nodes().getFirst().contexts().get("server")).contains("lobby");

            storage.appendHistory(new HistoryRecord(Instant.now(), "console", "test", "userAdd", uuid.toString(), "user.kit"));
            assertThat(storage.countHistory(uuid.toString())).isEqualTo(1);
            assertThat(storage.listHistory(uuid.toString(), 0, 10)).hasSize(1);

            storage.deleteGroup("staff");
            assertThat(storage.loadGroup("staff")).isEmpty();
            UserData afterDelete = storage.loadUser(uuid).orElseThrow();
            assertThat(afterDelete.primaryGroup()).isNull();
            assertThat(afterDelete.groups()).containsExactly("default");
        } finally {
            storage.close();
        }
    }

    static void assertLegacyJson(JdbcDatabaseContainer<?> container, boolean postgres) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement()) {
            if (postgres) {
                statement.execute("""
                        CREATE TABLE ae_user (
                          uuid UUID PRIMARY KEY,
                          name VARCHAR(16),
                          primary_group VARCHAR(64),
                          groups_json TEXT NOT NULL,
                          nodes_json TEXT NOT NULL,
                          temp_json TEXT NOT NULL,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("""
                        CREATE TABLE ae_group (
                          name VARCHAR(64) PRIMARY KEY,
                          weight INT NOT NULL,
                          parents_json TEXT NOT NULL,
                          nodes_json TEXT NOT NULL,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("""
                        CREATE TABLE ae_history (
                          id BIGSERIAL PRIMARY KEY,
                          at TIMESTAMPTZ NOT NULL,
                          actor VARCHAR(64) NOT NULL,
                          source VARCHAR(16) NOT NULL,
                          action VARCHAR(64) NOT NULL,
                          target VARCHAR(128) NOT NULL,
                          detail TEXT NOT NULL
                        )
                        """);
            } else {
                statement.execute("""
                        CREATE TABLE ae_user (
                          uuid CHAR(36) PRIMARY KEY,
                          name VARCHAR(16),
                          primary_group VARCHAR(64),
                          groups_json TEXT NOT NULL,
                          nodes_json TEXT NOT NULL,
                          temp_json TEXT NOT NULL,
                          created_at TIMESTAMP(6) NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP(6) NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("""
                        CREATE TABLE ae_group (
                          name VARCHAR(64) PRIMARY KEY,
                          weight INT NOT NULL,
                          parents_json TEXT NOT NULL,
                          nodes_json TEXT NOT NULL,
                          created_at TIMESTAMP(6) NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP(6) NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("""
                        CREATE TABLE ae_history (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          at TIMESTAMP(6) NOT NULL,
                          actor VARCHAR(64) NOT NULL,
                          source VARCHAR(16) NOT NULL,
                          action VARCHAR(64) NOT NULL,
                          target VARCHAR(128) NOT NULL,
                          detail TEXT NOT NULL
                        )
                        """);
            }
            statement.execute("""
                    INSERT INTO ae_group (name, weight, parents_json, nodes_json)
                    VALUES ('vip', 5, '["default"]', '[{"permission":"vip.fly","value":true,"contexts":{"server":"lobby"},"expiry":null}]')
                    """);
            statement.execute("""
                    INSERT INTO ae_user (uuid, name, primary_group, groups_json, nodes_json, temp_json)
                    VALUES ('00000000-0000-0000-0000-000000000099', 'Legacy', 'vip',
                    '["default","vip"]',
                    '[{"permission":"legacy.kit","value":true,"contexts":{},"expiry":null}]',
                    '[]')
                    """);
        }

        SqlStorage storage = storage(container);
        try {
            GroupData vip = storage.loadGroup("vip").orElseThrow();
            assertThat(vip.weight()).isEqualTo(5);
            assertThat(vip.parents()).containsExactly("default");
            assertThat(vip.nodes()).hasSize(1);
            assertThat(vip.nodes().getFirst().permission()).isEqualTo("vip.fly");
            assertThat(vip.nodes().getFirst().contexts().get("server")).contains("lobby");

            UserData user = storage.loadUser(UUID.fromString("00000000-0000-0000-0000-000000000099")).orElseThrow();
            assertThat(user.name()).isEqualTo("Legacy");
            assertThat(user.primaryGroup()).isEqualTo("vip");
            assertThat(user.groups()).containsExactlyInAnyOrder("default", "vip");
            assertThat(user.nodes()).hasSize(1);
            assertThat(user.nodes().getFirst().permission()).isEqualTo("legacy.kit");

            try (Connection connection = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword());
                 Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeQuery("SELECT groups_json FROM ae_user"))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        } finally {
            storage.close();
        }
    }
}
