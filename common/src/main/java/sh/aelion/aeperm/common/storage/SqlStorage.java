package sh.aelion.aeperm.common.storage;

import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import sh.aelion.aeperm.api.ContextSet;
import sh.aelion.aeperm.api.PermissionNode;
import sh.aelion.aeperm.common.calc.PermissionCalculator;
import sh.aelion.aeperm.common.config.AepermConfig;
import sh.aelion.aeperm.common.history.HistoryRecord;
import sh.aelion.aeperm.common.model.GroupData;
import sh.aelion.aeperm.common.model.UserData;
import sh.aelion.aeperm.common.storage.entity.GroupEntity;
import sh.aelion.aeperm.common.storage.entity.GroupNodeEntity;
import sh.aelion.aeperm.common.storage.entity.HistoryEntity;
import sh.aelion.aeperm.common.storage.entity.UserEntity;
import sh.aelion.aeperm.common.storage.entity.UserNodeEntity;
import sh.aelion.aeperm.common.storage.entity.UserTempGroupEntity;
import sh.aelion.aeperm.common.storage.hibernate.HibernateSessions;
import sh.aelion.sql.AelionDb;
import sh.aelion.sql.Dialect;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqlStorage implements Storage {

    private static final String USER_GRAPH = """
            SELECT u FROM UserEntity u
            LEFT JOIN FETCH u.primaryGroup
            LEFT JOIN FETCH u.groups
            LEFT JOIN FETCH u.tempGroups
            LEFT JOIN FETCH u.nodes n
            LEFT JOIN FETCH n.contexts
            WHERE u.uuid = :id
            """;

    private static final String USER_BY_NAME_GRAPH = """
            SELECT u FROM UserEntity u
            LEFT JOIN FETCH u.primaryGroup
            LEFT JOIN FETCH u.groups
            LEFT JOIN FETCH u.tempGroups
            LEFT JOIN FETCH u.nodes n
            LEFT JOIN FETCH n.contexts
            WHERE LOWER(u.name) = LOWER(:name)
            """;

    private static final String GROUP_GRAPH = """
            SELECT g FROM GroupEntity g
            LEFT JOIN FETCH g.parents
            LEFT JOIN FETCH g.nodes n
            LEFT JOIN FETCH n.contexts
            WHERE g.name = :name
            """;

    private final AepermConfig.StorageConfig config;
    private AelionDb db;
    private SessionFactory sessionFactory;

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
                .autoCommit(false)
                .open();
        Dialect dialect = db.dialect();
        String folder = dialect == Dialect.POSTGRES ? "postgresql" : "mariadb";
        Flyway.configure()
                .dataSource(db.dataSource())
                .locations(
                        "classpath:db/migration/" + folder,
                        "classpath:sh/aelion/aeperm/common/storage/flyway"
                )
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        sessionFactory = HibernateSessions.create(db.dataSource(), dialect);
        if (loadGroup(PermissionCalculator.DEFAULT_GROUP).isEmpty()) {
            saveGroup(new GroupData(PermissionCalculator.DEFAULT_GROUP));
        }
    }

    @Override
    public Optional<UserData> loadUser(UUID uuid) {
        return sessionFactory.fromTransaction(session -> {
            UserEntity entity = unique(session.createQuery(USER_GRAPH, UserEntity.class)
                    .setParameter("id", uuid)
                    .setReadOnly(true)
                    .getResultList());
            return entity == null ? Optional.empty() : Optional.of(toUserData(entity));
        });
    }

    @Override
    public Optional<UserData> findUserByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return sessionFactory.fromTransaction(session -> {
            UserEntity entity = unique(session.createQuery(USER_BY_NAME_GRAPH, UserEntity.class)
                    .setParameter("name", name)
                    .setReadOnly(true)
                    .getResultList());
            return entity == null ? Optional.empty() : Optional.of(toUserData(entity));
        });
    }

    @Override
    public void saveUser(UserData user) {
        sessionFactory.inTransaction(session -> {
            UserEntity entity = unique(session.createQuery(USER_GRAPH, UserEntity.class)
                    .setParameter("id", user.uuid())
                    .getResultList());
            if (entity == null) {
                entity = new UserEntity();
                entity.uuid(user.uuid());
                session.persist(entity);
            }
            applyUser(session, entity, user);
        });
    }

    @Override
    public Optional<GroupData> loadGroup(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return sessionFactory.fromTransaction(session -> {
            GroupEntity entity = unique(session.createQuery(GROUP_GRAPH, GroupEntity.class)
                    .setParameter("name", key)
                    .setReadOnly(true)
                    .getResultList());
            return entity == null ? Optional.empty() : Optional.of(toGroupData(entity));
        });
    }

    @Override
    public void saveGroup(GroupData group) {
        sessionFactory.inTransaction(session -> {
            GroupEntity entity = unique(session.createQuery(GROUP_GRAPH, GroupEntity.class)
                    .setParameter("name", group.name())
                    .getResultList());
            if (entity == null) {
                entity = new GroupEntity();
                entity.name(group.name());
                session.persist(entity);
            }
            applyGroup(session, entity, group);
        });
    }

    @Override
    public void deleteGroup(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        sessionFactory.inTransaction(session -> {
            GroupEntity entity = session.find(GroupEntity.class, key);
            if (entity != null) {
                session.remove(entity);
            }
        });
    }

    @Override
    public Set<String> listGroups() {
        return sessionFactory.fromTransaction(session -> new LinkedHashSet<>(session.createQuery(
                        "SELECT g.name FROM GroupEntity g ORDER BY g.name", String.class)
                .setReadOnly(true)
                .getResultList()));
    }

    @Override
    public List<String> listUserNames(String prefix, int limit) {
        if (prefix == null || prefix.isBlank() || limit <= 0) {
            return List.of();
        }
        String pattern = escapeLike(prefix.toLowerCase(Locale.ROOT)) + "%";
        int cap = Math.max(0, limit);
        return sessionFactory.fromTransaction(session -> session.createQuery(
                        """
                                SELECT u.name FROM UserEntity u
                                WHERE u.name IS NOT NULL AND u.name <> ''
                                  AND LOWER(u.name) LIKE :prefix ESCAPE '\\'
                                ORDER BY u.name
                                """, String.class)
                .setParameter("prefix", pattern)
                .setMaxResults(cap)
                .setReadOnly(true)
                .getResultList());
    }

    @Override
    public void appendHistory(HistoryRecord record) {
        sessionFactory.inTransaction(session -> {
            HistoryEntity entity = new HistoryEntity();
            entity.at(record.at());
            entity.actor(record.actor());
            entity.source(record.source());
            entity.action(record.action());
            entity.target(record.target());
            entity.detail(record.detail() == null ? "" : record.detail());
            session.persist(entity);
        });
    }

    @Override
    public List<HistoryRecord> listHistory(String targetFilter, int offset, int limit) {
        return sessionFactory.fromTransaction(session -> {
            var query = session.createQuery(historyListHql(targetFilter), HistoryEntity.class);
            bindHistoryFilter(query, targetFilter);
            return query.setFirstResult(Math.max(offset, 0))
                    .setMaxResults(Math.max(limit, 0))
                    .setReadOnly(true)
                    .getResultList()
                    .stream()
                    .map(SqlStorage::toHistory)
                    .toList();
        });
    }

    @Override
    public int countHistory(String targetFilter) {
        return sessionFactory.fromTransaction(session -> {
            var query = session.createQuery(historyCountHql(targetFilter), Long.class);
            bindHistoryFilter(query, targetFilter);
            Long count = query.setReadOnly(true).uniqueResult();
            return count == null ? 0 : count.intValue();
        });
    }

    @Override
    public void close() {
        if (sessionFactory != null) {
            sessionFactory.close();
            sessionFactory = null;
        }
        if (db != null) {
            db.close();
            db = null;
        }
    }

    SessionFactory sessionFactory() {
        return sessionFactory;
    }

    private static String historyListHql(String targetFilter) {
        if (targetFilter == null || targetFilter.isBlank()) {
            return "SELECT h FROM HistoryEntity h ORDER BY h.at DESC";
        }
        return "SELECT h FROM HistoryEntity h WHERE LOWER(h.target) LIKE LOWER(:filter) ESCAPE '\\' ORDER BY h.at DESC";
    }

    private static String historyCountHql(String targetFilter) {
        if (targetFilter == null || targetFilter.isBlank()) {
            return "SELECT COUNT(h) FROM HistoryEntity h";
        }
        return "SELECT COUNT(h) FROM HistoryEntity h WHERE LOWER(h.target) LIKE LOWER(:filter) ESCAPE '\\'";
    }

    private static void bindHistoryFilter(org.hibernate.query.Query<?> query, String targetFilter) {
        if (targetFilter != null && !targetFilter.isBlank()) {
            query.setParameter("filter", "%" + escapeLike(targetFilter) + "%");
        }
    }

    private void applyUser(Session session, UserEntity entity, UserData user) {
        entity.name(user.name());
        entity.primaryGroup(user.primaryGroup() == null ? null : ensureGroup(session, user.primaryGroup()));
        entity.groups().clear();
        for (String group : user.groups()) {
            ensureGroup(session, group);
            entity.groups().add(group);
        }
        entity.tempGroups().clear();
        for (UserData.TempMembership membership : user.tempMemberships()) {
            ensureGroup(session, membership.group());
            UserTempGroupEntity temp = new UserTempGroupEntity();
            temp.user(entity);
            temp.id().groupName(membership.group());
            temp.expiry(membership.expiry());
            entity.tempGroups().add(temp);
        }
        entity.nodes().clear();
        for (PermissionNode node : user.nodes()) {
            entity.nodes().add(toUserNode(entity, node));
        }
    }

    private void applyGroup(Session session, GroupEntity entity, GroupData group) {
        entity.weight(group.weight());
        entity.parents().clear();
        for (String parent : group.parents()) {
            ensureGroup(session, parent);
            entity.parents().add(parent);
        }
        entity.nodes().clear();
        for (PermissionNode node : group.nodes()) {
            entity.nodes().add(toGroupNode(entity, node));
        }
    }

    private GroupEntity ensureGroup(Session session, String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        GroupEntity existing = session.find(GroupEntity.class, name);
        if (existing != null) {
            return existing;
        }
        GroupEntity created = new GroupEntity();
        created.name(name);
        created.weight(0);
        session.persist(created);
        return created;
    }

    private static UserNodeEntity toUserNode(UserEntity user, PermissionNode node) {
        UserNodeEntity entity = new UserNodeEntity();
        entity.user(user);
        entity.permission(node.permission());
        entity.value(node.value());
        entity.expiry(node.expiry().orElse(null));
        entity.contexts().putAll(node.contexts().asMap());
        return entity;
    }

    private static GroupNodeEntity toGroupNode(GroupEntity group, PermissionNode node) {
        GroupNodeEntity entity = new GroupNodeEntity();
        entity.group(group);
        entity.permission(node.permission());
        entity.value(node.value());
        entity.expiry(node.expiry().orElse(null));
        entity.contexts().putAll(node.contexts().asMap());
        return entity;
    }

    private static UserData toUserData(UserEntity entity) {
        UserData user = new UserData(entity.uuid());
        user.name(entity.name());
        if (entity.primaryGroup() != null) {
            user.primaryGroup(entity.primaryGroup().name());
        }
        user.groups().addAll(entity.groups());
        for (UserTempGroupEntity temp : entity.tempGroups()) {
            user.tempMemberships().add(new UserData.TempMembership(temp.id().groupName(), temp.expiry()));
        }
        for (UserNodeEntity node : entity.nodes()) {
            user.nodes().add(toNode(node.permission(), node.value(), node.expiry(), node.contexts()));
        }
        return user;
    }

    private static GroupData toGroupData(GroupEntity entity) {
        GroupData group = new GroupData(entity.name());
        group.weight(entity.weight());
        group.parents().addAll(entity.parents());
        for (GroupNodeEntity node : entity.nodes()) {
            group.nodes().add(toNode(node.permission(), node.value(), node.expiry(), node.contexts()));
        }
        return group;
    }

    private static PermissionNode toNode(String permission, boolean value, Instant expiry, Map<String, String> contexts) {
        ContextSet.Builder builder = ContextSet.builder();
        if (contexts != null) {
            contexts.forEach(builder::with);
        }
        return new PermissionNode(permission, value, builder.build(), expiry);
    }

    private static HistoryRecord toHistory(HistoryEntity entity) {
        return new HistoryRecord(
                entity.at() == null ? Instant.EPOCH : entity.at(),
                entity.actor(),
                entity.source(),
                entity.action(),
                entity.target(),
                entity.detail()
        );
    }

    private static <T> T unique(List<T> rows) {
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
