package sh.aelion.aeperm.common.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.api.PermissionNode;
import sh.aelion.aeperm.common.calc.PermissionCalculator;
import sh.aelion.aeperm.common.config.AepermConfig;
import sh.aelion.aeperm.common.history.HistoryRecord;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;
import sh.aelion.sql.AelionDb;
import sh.aelion.sql.Col;
import sh.aelion.sql.Rows;
import sh.aelion.sql.Where;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqlStorage implements Storage {

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();
    private static final Type NODE_LIST = new TypeToken<List<NodeDto>>() {
    }.getType();
    private static final Type TEMP_LIST = new TypeToken<List<TempDto>>() {
    }.getType();

    private final AepermConfig.StorageConfig config;
    private final Gson gson = new GsonBuilder().create();
    private AelionDb db;

    public SqlStorage(AepermConfig.StorageConfig config) {
        this.config = config;
    }

    @Override
    public void init() {
        db = AelionDb.builder()
                .url(config.url())
                .user(config.user())
                .password(config.password())
                .maximumPoolSize(config.maximumPoolSize())
                .minimumIdle(config.minimumIdle())
                .poolName("aeperm-pool")
                .autoCommit(true)
                .open();

        db.schema().table("ae_user")
                .column("uuid", Col.uuid().primaryKey())
                .column("name", Col.varchar(16))
                .column("primary_group", Col.varchar(64))
                .column("groups_json", Col.text().notNull())
                .column("nodes_json", Col.text().notNull())
                .column("temp_json", Col.text().notNull())
                .column("created_at", Col.timestamp().defaultNow())
                .column("updated_at", Col.timestamp().defaultNow())
                .index("ae_user_name_idx", "name")
                .create();
        db.schema().table("ae_group")
                .column("name", Col.varchar(64).primaryKey())
                .column("weight", Col.integer().notNull())
                .column("parents_json", Col.text().notNull())
                .column("nodes_json", Col.text().notNull())
                .column("created_at", Col.timestamp().defaultNow())
                .column("updated_at", Col.timestamp().defaultNow())
                .create();
        db.schema().table("ae_history")
                .column("id", Col.identity())
                .column("at", Col.timestamp().notNull())
                .column("actor", Col.varchar(64).notNull())
                .column("source", Col.varchar(16).notNull())
                .column("action", Col.varchar(64).notNull())
                .column("target", Col.varchar(128).notNull())
                .column("detail", Col.text().notNull())
                .index("ae_history_target_idx", "target", "at")
                .create();

        if (loadGroup(PermissionCalculator.DEFAULT_GROUP).isEmpty()) {
            saveGroup(new GroupData(PermissionCalculator.DEFAULT_GROUP));
        }
    }

    @Override
    public Optional<UserData> loadUser(UUID uuid) {
        return db.query()
                .select("uuid", "name", "primary_group", "groups_json", "nodes_json", "temp_json")
                .from("ae_user")
                .where(Where.eq("uuid", uuid))
                .one(this::readUser);
    }

    @Override
    public Optional<UserData> findUserByName(String name) {
        return db.query()
                .select("uuid", "name", "primary_group", "groups_json", "nodes_json", "temp_json")
                .from("ae_user")
                .where(Where.eqIgnoreCase("name", name))
                .limit(1)
                .one(this::readUser);
    }

    @Override
    public void saveUser(UserData user) {
        db.query().insert("ae_user")
                .value("uuid", user.uuid())
                .value("name", user.name())
                .value("primary_group", user.primaryGroup())
                .value("groups_json", gson.toJson(new ArrayList<>(user.groups())))
                .value("nodes_json", gson.toJson(toNodeDtos(user.nodes())))
                .value("temp_json", gson.toJson(toTempDtos(user.tempMemberships())))
                .now("created_at")
                .now("updated_at")
                .onConflict("uuid")
                .update("name", "primary_group", "groups_json", "nodes_json", "temp_json")
                .updateNow("updated_at")
                .execute();
    }

    @Override
    public Optional<GroupData> loadGroup(String name) {
        return db.query()
                .select("name", "weight", "parents_json", "nodes_json")
                .from("ae_group")
                .where(Where.eq("name", name.toLowerCase(Locale.ROOT)))
                .one(this::readGroup);
    }

    @Override
    public void saveGroup(GroupData group) {
        db.query().insert("ae_group")
                .value("name", group.name())
                .value("weight", group.weight())
                .value("parents_json", gson.toJson(new ArrayList<>(group.parents())))
                .value("nodes_json", gson.toJson(toNodeDtos(group.nodes())))
                .now("created_at")
                .now("updated_at")
                .onConflict("name")
                .update("weight", "parents_json", "nodes_json")
                .updateNow("updated_at")
                .execute();
    }

    @Override
    public void deleteGroup(String name) {
        db.query().delete("ae_group")
                .where(Where.eq("name", name.toLowerCase(Locale.ROOT)))
                .execute();
    }

    @Override
    public Set<String> listGroups() {
        return new LinkedHashSet<>(db.query()
                .select("name")
                .from("ae_group")
                .orderBy("name")
                .list(rs -> rs.getString("name")));
    }

    @Override
    public Set<String> listUserNames() {
        return new LinkedHashSet<>(db.query()
                .select("name")
                .from("ae_user")
                .where(Where.isNotNull("name").and(Where.ne("name", "")))
                .list(rs -> rs.getString("name")));
    }

    @Override
    public List<String> listUserNames(String prefix, int limit) {
        int cap = Math.max(0, limit);
        if (cap == 0) {
            return List.of();
        }
        String pattern = escapeLike(prefix == null ? "" : prefix) + "%";
        return db.query()
                .select("name")
                .from("ae_user")
                .where(Where.isNotNull("name")
                        .and(Where.ne("name", ""))
                        .and(Where.ilike("name", pattern)))
                .orderBy("name")
                .limit(cap)
                .list(rs -> rs.getString("name"));
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public void appendHistory(HistoryRecord record) {
        db.query().insert("ae_history")
                .value("at", record.at())
                .value("actor", record.actor())
                .value("source", record.source())
                .value("action", record.action())
                .value("target", record.target())
                .value("detail", record.detail() == null ? "" : record.detail())
                .execute();
    }

    @Override
    public List<HistoryRecord> listHistory(String targetFilter, int offset, int limit) {
        var select = db.query()
                .select("at", "actor", "source", "action", "target", "detail")
                .from("ae_history");
        if (targetFilter != null && !targetFilter.isBlank()) {
            select.where(Where.ilike("target", "%" + targetFilter + "%"));
        }
        return select.orderBy("at desc")
                .limit(Math.max(limit, 0))
                .offset(Math.max(offset, 0))
                .list(rs -> new HistoryRecord(
                        Optional.ofNullable(Rows.instant(rs, "at")).orElse(Instant.EPOCH),
                        rs.getString("actor"),
                        rs.getString("source"),
                        rs.getString("action"),
                        rs.getString("target"),
                        rs.getString("detail")
                ));
    }

    @Override
    public int countHistory(String targetFilter) {
        var select = db.query().select("COUNT(*)").from("ae_history");
        if (targetFilter != null && !targetFilter.isBlank()) {
            select.where(Where.ilike("target", "%" + targetFilter + "%"));
        }
        return select.one(rs -> rs.getInt(1)).orElse(0);
    }

    @Override
    public void close() {
        if (db != null) {
            db.close();
        }
    }

    private UserData readUser(ResultSet rs) throws SQLException {
        UserData user = new UserData(Rows.uuid(rs, "uuid"));
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

    private GroupData readGroup(ResultSet rs) throws SQLException {
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
