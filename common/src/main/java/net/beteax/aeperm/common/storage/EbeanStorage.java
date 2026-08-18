package net.beteax.aeperm.common.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.beteax.aeperm.api.ContextSet;
import net.beteax.aeperm.api.PermissionNode;
import net.beteax.aeperm.common.calc.PermissionCalculator;
import net.beteax.aeperm.common.config.AepermConfig;
import net.beteax.aeperm.common.history.HistoryRecord;
import net.beteax.aeperm.common.model.GroupData;
import net.beteax.aeperm.common.model.UserData;
import org.postgresql.ds.PGSimpleDataSource;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EbeanStorage implements Storage {

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();
    private static final Type NODE_LIST = new TypeToken<List<NodeDto>>() {
    }.getType();
    private static final Type TEMP_LIST = new TypeToken<List<TempDto>>() {
    }.getType();

    private final AepermConfig.StorageConfig config;
    private final Gson gson = new GsonBuilder().create();
    private HikariDataSource dataSource;

    public EbeanStorage(AepermConfig.StorageConfig config) {
        this.config = config;
    }

    @Override
    public void init() {
        HikariConfig hikari = new HikariConfig();
        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setUrl(config.url());
        pg.setUser(config.user());
        pg.setPassword(config.password());
        hikari.setDataSource(pg);
        hikari.setMaximumPoolSize(config.maximumPoolSize());
        hikari.setMinimumIdle(config.minimumIdle());
        hikari.setPoolName("aeperm-pool");
        hikari.setAutoCommit(true);
        dataSource = new HikariDataSource(hikari);
        migrate();
        if (loadGroup(PermissionCalculator.DEFAULT_GROUP).isEmpty()) {
            saveGroup(new GroupData(PermissionCalculator.DEFAULT_GROUP));
        }
    }

    private void migrate() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ae_user (
                      uuid UUID PRIMARY KEY,
                      name VARCHAR(16),
                      primary_group VARCHAR(64),
                      groups_json TEXT NOT NULL DEFAULT '[]',
                      nodes_json TEXT NOT NULL DEFAULT '[]',
                      temp_json TEXT NOT NULL DEFAULT '[]',
                      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ae_group (
                      name VARCHAR(64) PRIMARY KEY,
                      weight INT NOT NULL DEFAULT 0,
                      parents_json TEXT NOT NULL DEFAULT '[]',
                      nodes_json TEXT NOT NULL DEFAULT '[]',
                      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS ae_user_name_idx ON ae_user (lower(name))");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ae_history (
                      id BIGSERIAL PRIMARY KEY,
                      at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      actor VARCHAR(64) NOT NULL,
                      source VARCHAR(16) NOT NULL,
                      action VARCHAR(64) NOT NULL,
                      target VARCHAR(128) NOT NULL,
                      detail TEXT NOT NULL DEFAULT ''
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS ae_history_target_idx ON ae_history (lower(target), at DESC)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate schema", e);
        }
    }

    @Override
    public Optional<UserData> loadUser(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, name, primary_group, groups_json, nodes_json, temp_json FROM ae_user WHERE uuid = ?")) {
            statement.setObject(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("loadUser failed", e);
        }
    }

    @Override
    public Optional<UserData> findUserByName(String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, name, primary_group, groups_json, nodes_json, temp_json FROM ae_user WHERE lower(name) = lower(?) LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findUserByName failed", e);
        }
    }

    @Override
    public void saveUser(UserData user) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ae_user (uuid, name, primary_group, groups_json, nodes_json, temp_json, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, NOW())
                     ON CONFLICT (uuid) DO UPDATE SET
                       name = EXCLUDED.name,
                       primary_group = EXCLUDED.primary_group,
                       groups_json = EXCLUDED.groups_json,
                       nodes_json = EXCLUDED.nodes_json,
                       temp_json = EXCLUDED.temp_json,
                       updated_at = NOW()
                     """)) {
            statement.setObject(1, user.uuid());
            statement.setString(2, user.name());
            statement.setString(3, user.primaryGroup());
            statement.setString(4, gson.toJson(new ArrayList<>(user.groups())));
            statement.setString(5, gson.toJson(toNodeDtos(user.nodes())));
            statement.setString(6, gson.toJson(toTempDtos(user.tempMemberships())));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("saveUser failed", e);
        }
    }

    @Override
    public Optional<GroupData> loadGroup(String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT name, weight, parents_json, nodes_json FROM ae_group WHERE name = ?")) {
            statement.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapGroup(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("loadGroup failed", e);
        }
    }

    @Override
    public void saveGroup(GroupData group) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ae_group (name, weight, parents_json, nodes_json, updated_at)
                     VALUES (?, ?, ?, ?, NOW())
                     ON CONFLICT (name) DO UPDATE SET
                       weight = EXCLUDED.weight,
                       parents_json = EXCLUDED.parents_json,
                       nodes_json = EXCLUDED.nodes_json,
                       updated_at = NOW()
                     """)) {
            statement.setString(1, group.name());
            statement.setInt(2, group.weight());
            statement.setString(3, gson.toJson(new ArrayList<>(group.parents())));
            statement.setString(4, gson.toJson(toNodeDtos(group.nodes())));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("saveGroup failed", e);
        }
    }

    @Override
    public void deleteGroup(String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM ae_group WHERE name = ?")) {
            statement.setString(1, name.toLowerCase(Locale.ROOT));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("deleteGroup failed", e);
        }
    }

    @Override
    public Set<String> listGroups() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT name FROM ae_group ORDER BY name");
             ResultSet rs = statement.executeQuery()) {
            Set<String> names = new LinkedHashSet<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            return names;
        } catch (SQLException e) {
            throw new IllegalStateException("listGroups failed", e);
        }
    }

    @Override
    public Set<String> listUserNames() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT name FROM ae_user WHERE name IS NOT NULL AND name <> ''");
             ResultSet rs = statement.executeQuery()) {
            Set<String> names = new LinkedHashSet<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            return names;
        } catch (SQLException e) {
            throw new IllegalStateException("listUserNames failed", e);
        }
    }

    @Override
    public void appendHistory(HistoryRecord record) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO ae_history (at, actor, source, action, target, detail) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setTimestamp(1, Timestamp.from(record.at()));
            statement.setString(2, record.actor());
            statement.setString(3, record.source());
            statement.setString(4, record.action());
            statement.setString(5, record.target());
            statement.setString(6, record.detail() == null ? "" : record.detail());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("appendHistory failed", e);
        }
    }

    @Override
    public List<HistoryRecord> listHistory(String targetFilter, int offset, int limit) {
        String sql = "SELECT at, actor, source, action, target, detail FROM ae_history";
        boolean filtered = targetFilter != null && !targetFilter.isBlank();
        if (filtered) {
            sql += " WHERE lower(target) LIKE lower(?)";
        }
        sql += " ORDER BY at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int idx = 1;
            if (filtered) {
                statement.setString(idx++, "%" + targetFilter + "%");
            }
            statement.setInt(idx++, Math.max(limit, 0));
            statement.setInt(idx, Math.max(offset, 0));
            try (ResultSet rs = statement.executeQuery()) {
                List<HistoryRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    Timestamp at = rs.getTimestamp("at");
                    rows.add(new HistoryRecord(
                            at == null ? Instant.now() : at.toInstant(),
                            rs.getString("actor"),
                            rs.getString("source"),
                            rs.getString("action"),
                            rs.getString("target"),
                            rs.getString("detail")
                    ));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listHistory failed", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private UserData mapUser(ResultSet rs) throws SQLException {
        UserData user = new UserData((UUID) rs.getObject("uuid"));
        user.name(rs.getString("name"));
        user.primaryGroup(rs.getString("primary_group"));
        List<String> groups = gson.fromJson(nullToEmptyArray(rs.getString("groups_json")), STRING_LIST);
        if (groups != null) {
            user.groups().addAll(groups);
        }
        List<NodeDto> nodes = gson.fromJson(nullToEmptyArray(rs.getString("nodes_json")), NODE_LIST);
        if (nodes != null) {
            for (NodeDto dto : nodes) {
                user.nodes().add(dto.toNode());
            }
        }
        List<TempDto> temps = gson.fromJson(nullToEmptyArray(rs.getString("temp_json")), TEMP_LIST);
        if (temps != null) {
            for (TempDto dto : temps) {
                user.tempMemberships().add(new UserData.TempMembership(
                        dto.group,
                        dto.expiry == null ? null : Instant.parse(dto.expiry)
                ));
            }
        }
        return user;
    }

    private GroupData mapGroup(ResultSet rs) throws SQLException {
        GroupData group = new GroupData(rs.getString("name"));
        group.weight(rs.getInt("weight"));
        List<String> parents = gson.fromJson(nullToEmptyArray(rs.getString("parents_json")), STRING_LIST);
        if (parents != null) {
            group.parents().addAll(parents);
        }
        List<NodeDto> nodes = gson.fromJson(nullToEmptyArray(rs.getString("nodes_json")), NODE_LIST);
        if (nodes != null) {
            for (NodeDto dto : nodes) {
                group.nodes().add(dto.toNode());
            }
        }
        return group;
    }

    private List<NodeDto> toNodeDtos(List<PermissionNode> nodes) {
        List<NodeDto> list = new ArrayList<>();
        for (PermissionNode node : nodes) {
            NodeDto dto = new NodeDto();
            dto.permission = node.permission();
            dto.value = node.value();
            dto.contexts = node.contexts().asMap();
            dto.expiry = node.expiry().map(Instant::toString).orElse(null);
            list.add(dto);
        }
        return list;
    }

    private List<TempDto> toTempDtos(List<UserData.TempMembership> memberships) {
        List<TempDto> list = new ArrayList<>();
        for (UserData.TempMembership membership : memberships) {
            TempDto dto = new TempDto();
            dto.group = membership.group();
            dto.expiry = membership.expiry() == null ? null : membership.expiry().toString();
            list.add(dto);
        }
        return list;
    }

    private String nullToEmptyArray(String json) {
        return json == null || json.isBlank() ? "[]" : json;
    }

    private static final class NodeDto {
        String permission;
        boolean value = true;
        Map<String, String> contexts;
        String expiry;

        PermissionNode toNode() {
            ContextSet.Builder builder = ContextSet.builder();
            if (contexts != null) {
                contexts.forEach(builder::with);
            }
            Instant exp = expiry == null ? null : Instant.parse(expiry);
            return new PermissionNode(permission, value, builder.build(), exp);
        }
    }

    private static final class TempDto {
        String group;
        String expiry;
    }
}
