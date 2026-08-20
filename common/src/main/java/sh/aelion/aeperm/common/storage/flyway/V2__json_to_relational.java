package sh.aelion.aeperm.common.storage.flyway;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Copies legacy Gson TEXT blobs into child tables, then drops the JSON columns.
 * No-op on databases that already have the normalized shape.
 */
public final class V2__json_to_relational extends BaseJavaMigration {

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();
    private static final Type NODE_LIST = new TypeToken<List<NodeDto>>() {
    }.getType();
    private static final Type TEMP_LIST = new TypeToken<List<TempDto>>() {
    }.getType();

    private final Gson gson = new GsonBuilder().create();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean postgres = isPostgres(connection);
        boolean hasUserJson = columnExists(connection, "ae_user", "groups_json")
                || columnExists(connection, "ae_user", "nodes_json")
                || columnExists(connection, "ae_user", "temp_json");
        boolean hasGroupJson = columnExists(connection, "ae_group", "parents_json")
                || columnExists(connection, "ae_group", "nodes_json");
        if (hasGroupJson) {
            migrateGroups(connection, postgres);
            dropGroupJsonColumns(connection);
        }
        if (hasUserJson) {
            migrateUsers(connection, postgres);
            dropUserJsonColumns(connection);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE ae_user SET primary_group = NULL WHERE primary_group IS NOT NULL AND TRIM(primary_group) = ''");
        }
        ensurePrimaryGroupForeignKey(connection, postgres);
        if (!postgres) {
            ensureMariaNameLower(connection);
        }
    }

    private void migrateGroups(Connection connection, boolean postgres) throws SQLException {
        String sql = "SELECT name, parents_json, nodes_json FROM ae_group";
        List<GroupRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new GroupRow(
                        rs.getString("name"),
                        parseStrings(column(rs, "parents_json")),
                        parseNodes(column(rs, "nodes_json"))
                ));
            }
        }
        Set<String> needed = new LinkedHashSet<>();
        for (GroupRow row : rows) {
            needed.addAll(row.parents);
        }
        for (String name : needed) {
            ensureGroup(connection, postgres, name);
        }
        for (GroupRow row : rows) {
            insertParents(connection, row.name, row.parents);
            insertGroupNodes(connection, postgres, row.name, row.nodes);
        }
    }

    private void migrateUsers(Connection connection, boolean postgres) throws SQLException {
        String sql = "SELECT uuid, primary_group, groups_json, nodes_json, temp_json FROM ae_user";
        List<UserRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new UserRow(
                        readUuid(rs, postgres),
                        rs.getString("primary_group"),
                        parseStrings(column(rs, "groups_json")),
                        parseNodes(column(rs, "nodes_json")),
                        parseTemps(column(rs, "temp_json"))
                ));
            }
        }
        Set<String> needed = new LinkedHashSet<>();
        for (UserRow row : rows) {
            if (row.primaryGroup != null && !row.primaryGroup.isBlank()) {
                needed.add(row.primaryGroup.toLowerCase(Locale.ROOT));
            }
            needed.addAll(row.groups);
            for (TempDto temp : row.temps) {
                if (temp.group != null && !temp.group.isBlank()) {
                    needed.add(temp.group.toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String name : needed) {
            ensureGroup(connection, postgres, name);
        }
        for (UserRow row : rows) {
            insertUserGroups(connection, postgres, row.uuid, row.groups);
            insertUserTemps(connection, postgres, row.uuid, row.temps);
            insertUserNodes(connection, postgres, row.uuid, row.nodes);
        }
    }

    private void insertParents(Connection connection, String group, List<String> parents) throws SQLException {
        if (parents.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ae_group_parent (group_name, parent_name) VALUES (?, ?)")) {
            for (String parent : parents) {
                if (parent == null || parent.isBlank()) {
                    continue;
                }
                ps.setString(1, group);
                ps.setString(2, parent.toLowerCase(Locale.ROOT));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertGroupNodes(Connection connection, boolean postgres, String group, List<NodeDto> nodes)
            throws SQLException {
        for (NodeDto node : nodes) {
            long id = insertNode(connection, postgres,
                    "INSERT INTO ae_group_node (group_name, permission, value, expiry) VALUES (?, ?, ?, ?)",
                    ps -> {
                        ps.setString(1, group);
                        bindNode(ps, 2, node, postgres);
                    });
            insertContexts(connection, "ae_group_node_context", id, node);
        }
    }

    private void insertUserGroups(Connection connection, boolean postgres, UUID uuid, List<String> groups)
            throws SQLException {
        if (groups.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ae_user_group (user_uuid, group_name) VALUES (?, ?)")) {
            for (String group : groups) {
                if (group == null || group.isBlank()) {
                    continue;
                }
                bindUuid(ps, 1, uuid, postgres);
                ps.setString(2, group.toLowerCase(Locale.ROOT));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertUserTemps(Connection connection, boolean postgres, UUID uuid, List<TempDto> temps)
            throws SQLException {
        if (temps.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ae_user_temp_group (user_uuid, group_name, expiry) VALUES (?, ?, ?)")) {
            for (TempDto temp : temps) {
                if (temp.group == null || temp.group.isBlank() || temp.expiry == null) {
                    continue;
                }
                bindUuid(ps, 1, uuid, postgres);
                ps.setString(2, temp.group.toLowerCase(Locale.ROOT));
                ps.setTimestamp(3, Timestamp.from(Instant.parse(temp.expiry)));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertUserNodes(Connection connection, boolean postgres, UUID uuid, List<NodeDto> nodes)
            throws SQLException {
        for (NodeDto node : nodes) {
            long id = insertNode(connection, postgres,
                    "INSERT INTO ae_user_node (user_uuid, permission, value, expiry) VALUES (?, ?, ?, ?)",
                    ps -> {
                        bindUuid(ps, 1, uuid, postgres);
                        bindNode(ps, 2, node, postgres);
                    });
            insertContexts(connection, "ae_user_node_context", id, node);
        }
    }

    private long insertNode(Connection connection, boolean postgres, String sql, Binder binder)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Missing generated node id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void bindNode(PreparedStatement ps, int permissionIndex, NodeDto node, boolean postgres)
            throws SQLException {
        ps.setString(permissionIndex, node.permission == null ? "" : node.permission.toLowerCase(Locale.ROOT));
        ps.setBoolean(permissionIndex + 1, node.value);
        if (node.expiry == null || node.expiry.isBlank()) {
            ps.setNull(permissionIndex + 2, postgres ? Types.TIMESTAMP_WITH_TIMEZONE : Types.TIMESTAMP);
        } else {
            ps.setTimestamp(permissionIndex + 2, Timestamp.from(Instant.parse(node.expiry)));
        }
    }

    private void insertContexts(Connection connection, String table, long nodeId, NodeDto node)
            throws SQLException {
        if (node.contexts == null || node.contexts.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + table + " (node_id, ctx_key, ctx_value) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> entry : node.contexts.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                ps.setLong(1, nodeId);
                ps.setString(2, entry.getKey().toLowerCase(Locale.ROOT));
                ps.setString(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void ensureGroup(Connection connection, boolean postgres, String raw) throws SQLException {
        String name = raw.toLowerCase(Locale.ROOT);
        boolean jsonCols = columnExists(connection, "ae_group", "parents_json");
        String sql;
        if (jsonCols) {
            sql = postgres
                    ? "INSERT INTO ae_group (name, weight, parents_json, nodes_json, created_at, updated_at) VALUES (?, 0, '[]', '[]', NOW(), NOW()) ON CONFLICT (name) DO NOTHING"
                    : "INSERT IGNORE INTO ae_group (name, weight, parents_json, nodes_json, created_at, updated_at) VALUES (?, 0, '[]', '[]', NOW(), NOW())";
        } else {
            sql = postgres
                    ? "INSERT INTO ae_group (name, weight, created_at, updated_at) VALUES (?, 0, NOW(), NOW()) ON CONFLICT (name) DO NOTHING"
                    : "INSERT IGNORE INTO ae_group (name, weight, created_at, updated_at) VALUES (?, 0, NOW(), NOW())";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    private void dropUserJsonColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ae_user DROP COLUMN IF EXISTS groups_json");
            statement.execute("ALTER TABLE ae_user DROP COLUMN IF EXISTS nodes_json");
            statement.execute("ALTER TABLE ae_user DROP COLUMN IF EXISTS temp_json");
        }
    }

    private void dropGroupJsonColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ae_group DROP COLUMN IF EXISTS parents_json");
            statement.execute("ALTER TABLE ae_group DROP COLUMN IF EXISTS nodes_json");
        }
    }

    private void ensurePrimaryGroupForeignKey(Connection connection, boolean postgres) throws SQLException {
        if (foreignKeyExists(connection, postgres, "ae_user", "ae_user_primary_group_fk")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (postgres) {
                statement.execute("""
                        ALTER TABLE ae_user
                        ADD CONSTRAINT ae_user_primary_group_fk
                        FOREIGN KEY (primary_group) REFERENCES ae_group (name) ON DELETE SET NULL
                        """);
            } else {
                statement.execute("""
                        ALTER TABLE ae_user
                        ADD CONSTRAINT ae_user_primary_group_fk
                        FOREIGN KEY (primary_group) REFERENCES ae_group (name) ON DELETE SET NULL
                        """);
            }
        }
    }

    private void ensureMariaNameLower(Connection connection) throws SQLException {
        if (columnExists(connection, "ae_user", "name_lower")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ae_user ADD COLUMN name_lower VARCHAR(16) AS (LOWER(name)) VIRTUAL");
            statement.execute("CREATE UNIQUE INDEX ae_user_name_lower_uq ON ae_user (name_lower)");
        }
    }

    private static boolean foreignKeyExists(Connection connection, boolean postgres, String table, String name)
            throws SQLException {
        String sql = postgres
                ? """
                SELECT 1 FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                WHERE c.conname = ? AND t.relname = ?
                """
                : """
                SELECT 1 FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = ? AND constraint_name = ? AND constraint_type = 'FOREIGN KEY'
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (postgres) {
                ps.setString(1, name);
                ps.setString(2, table);
            } else {
                ps.setString(1, table);
                ps.setString(2, name);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table.toUpperCase(Locale.ROOT),
                column.toUpperCase(Locale.ROOT))) {
            return rs.next();
        }
    }

    private static boolean isPostgres(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        return product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
    }

    private List<String> parseStrings(String json) {
        List<String> values = gson.fromJson(nullToEmptyArray(json), STRING_LIST);
        return values == null ? List.of() : values;
    }

    private List<NodeDto> parseNodes(String json) {
        List<NodeDto> values = gson.fromJson(nullToEmptyArray(json), NODE_LIST);
        return values == null ? List.of() : values;
    }

    private List<TempDto> parseTemps(String json) {
        List<TempDto> values = gson.fromJson(nullToEmptyArray(json), TEMP_LIST);
        return values == null ? List.of() : values;
    }

    private static String nullToEmptyArray(String json) {
        return json == null || json.isBlank() ? "[]" : json;
    }

    private static String column(ResultSet rs, String name) throws SQLException {
        try {
            return rs.getString(name);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static UUID readUuid(ResultSet rs, boolean postgres) throws SQLException {
        if (postgres) {
            Object value = rs.getObject("uuid");
            if (value instanceof UUID uuid) {
                return uuid;
            }
            return UUID.fromString(String.valueOf(value));
        }
        return UUID.fromString(rs.getString("uuid"));
    }

    private static void bindUuid(PreparedStatement ps, int index, UUID uuid, boolean postgres) throws SQLException {
        if (postgres) {
            ps.setObject(index, uuid);
        } else {
            ps.setString(index, uuid.toString());
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private record GroupRow(String name, List<String> parents, List<NodeDto> nodes) {
    }

    private record UserRow(UUID uuid, String primaryGroup, List<String> groups, List<NodeDto> nodes, List<TempDto> temps) {
    }

    static final class NodeDto {
        String permission;
        boolean value = true;
        Map<String, String> contexts;
        String expiry;
    }

    static final class TempDto {
        String group;
        String expiry;
    }
}
