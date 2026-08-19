package sh.aelion.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AelionDb implements AutoCloseable {

    private final Dialect dialect;
    private final HikariDataSource dataSource;
    private final Duration cacheTtl;
    private final ConcurrentMap<String, NamedCache> caches = new ConcurrentHashMap<>();
    private final ThreadLocal<Held> held = new ThreadLocal<>();

    AelionDb(Dialect dialect, HikariDataSource dataSource, Duration cacheTtl) {
        this.dialect = dialect;
        this.dataSource = dataSource;
        this.cacheTtl = cacheTtl;
    }

    public static AelionDb preview(Dialect dialect) {
        return preview(dialect, null);
    }

    public static AelionDb preview(Dialect dialect, Duration cacheTtl) {
        return new AelionDb(Objects.requireNonNull(dialect, "dialect"), null, cacheTtl);
    }

    public static AelionDb open(String url, String user, String password) {
        return builder().url(url).user(user).password(password).open();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Dialect dialect() {
        return dialect;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Schema schema() {
        return new Schema(this);
    }

    public Query query() {
        return new Query(this);
    }

    public NamedCache cache(String name) {
        if (cacheTtl == null) {
            throw new IllegalStateException("Cache is disabled. Call AelionDb.builder().cacheTtl(...) first.");
        }
        return caches.computeIfAbsent(name, ignored -> new NamedCache(cacheTtl));
    }

    public <T> T inTransaction(SqlWork<T> work) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new SqlException(e);
        }
        Held previous = held.get();
        Held current = new Held(connection, true);
        current.depth = 1;
        held.set(current);
        boolean committed = false;
        try {
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            committed = true;
            return result;
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // keep original
            }
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new SqlException(e.getMessage(), e);
        } finally {
            held.set(previous);
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // close anyway
            }
            try {
                connection.close();
            } catch (SQLException ignored) {
                // ignore
            }
            if (!committed) {
                // already rolled back
            }
        }
    }

    Connection connection() throws SQLException {
        if (dataSource == null) {
            throw new SqlException("preview AelionDb cannot execute SQL");
        }
        Held current = held.get();
        if (current != null) {
            current.depth++;
            return current.connection;
        }
        Connection connection = dataSource.getConnection();
        Held created = new Held(connection, false);
        created.depth = 1;
        held.set(created);
        return connection;
    }

    void release() {
        Held current = held.get();
        if (current == null) {
            return;
        }
        current.depth--;
        if (current.depth > 0 || current.transaction) {
            return;
        }
        held.remove();
        try {
            current.connection.close();
        } catch (SQLException ignored) {
            // ignore
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public static final class Builder {
        private String url;
        private String user;
        private String password;
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private String poolName = "aesql";
        private boolean autoCommit = true;
        private Duration cacheTtl;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }

        public Builder poolName(String poolName) {
            this.poolName = poolName;
            return this;
        }

        public Builder autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        public AelionDb open() {
            Objects.requireNonNull(url, "url");
            Dialect dialect = Dialect.fromUrl(url);
            quietPoolLogging();
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(url);
            hikari.setUsername(user);
            hikari.setPassword(password);
            hikari.setDriverClassName(dialect == Dialect.POSTGRES
                    ? "org.postgresql.Driver"
                    : "org.mariadb.jdbc.Driver");
            hikari.setMaximumPoolSize(maximumPoolSize);
            hikari.setMinimumIdle(minimumIdle);
            hikari.setPoolName(poolName);
            hikari.setAutoCommit(autoCommit);
            return new AelionDb(dialect, new HikariDataSource(hikari), cacheTtl);
        }
    }

    /** Drop Hikari startup INFO chatter (unshaded and relocated packages). */
    private static void quietPoolLogging() {
        // Prefer Log4j Configurator. Do not call JUL setLevel: Velocity's Log4j JUL bridge
        // warns that setLevel is ignored and cannot change the underlying logger.
        Class<?> levelClass;
        Object warn;
        Class<?> configurator;
        try {
            levelClass = Class.forName("org.apache.logging.log4j.Level");
            warn = levelClass.getField("WARN").get(null);
            configurator = Class.forName("org.apache.logging.log4j.core.config.Configurator");
        } catch (ReflectiveOperationException ignored) {
            return;
        }
        for (String name : List.of(
                "com.zaxxer.hikari",
                "com.zaxxer.hikari.HikariDataSource",
                "com.zaxxer.hikari.pool.HikariPool",
                "sh.aelion.libs.hikari",
                "sh.aelion.libs.hikari.HikariDataSource",
                "sh.aelion.libs.hikari.pool.HikariPool",
                "sh.aelion.aeperm.libs.hikari",
                "sh.aelion.aeperm.libs.hikari.HikariDataSource",
                "sh.aelion.aeperm.libs.hikari.pool.HikariPool"
        )) {
            try {
                configurator.getMethod("setLevel", String.class, levelClass).invoke(null, name, warn);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }

    private static final class Held {
        private final Connection connection;
        private final boolean transaction;
        private int depth;

        private Held(Connection connection, boolean transaction) {
            this.connection = connection;
            this.transaction = transaction;
        }
    }
}
