package sh.aelion.sql;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlRenderTest {

    @Test
    void dialectFromUrl() {
        assertThat(Dialect.fromUrl("jdbc:postgresql://localhost/db")).isEqualTo(Dialect.POSTGRES);
        assertThat(Dialect.fromUrl("jdbc:pgsql://localhost/db")).isEqualTo(Dialect.POSTGRES);
        assertThat(Dialect.fromUrl("jdbc:mariadb://localhost/db")).isEqualTo(Dialect.MARIADB);
        assertThat(Dialect.fromUrl("jdbc:mysql://localhost/db")).isEqualTo(Dialect.MARIADB);
        assertThatThrownBy(() -> Dialect.fromUrl("jdbc:h2:mem:"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dialect.fromUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quotesIdentifiers() {
        assertThat(Dialect.POSTGRES.quote("ae_user")).isEqualTo("\"ae_user\"");
        assertThat(Dialect.MARIADB.quote("ae_user")).isEqualTo("`ae_user`");
    }

    @Test
    void createTablePostgresVsMariadb() {
        String pg = tableSql(Dialect.POSTGRES);
        String maria = tableSql(Dialect.MARIADB);
        assertThat(pg).contains("CREATE TABLE IF NOT EXISTS \"ae_user\"");
        assertThat(pg).contains("\"uuid\" UUID PRIMARY KEY");
        assertThat(pg).contains("\"created_at\" TIMESTAMPTZ NOT NULL DEFAULT NOW()");
        assertThat(pg).contains("CREATE INDEX IF NOT EXISTS \"ae_user_name_idx\" ON \"ae_user\" (\"name\")");
        assertThat(maria).contains("CREATE TABLE IF NOT EXISTS `ae_user`");
        assertThat(maria).contains("`uuid` CHAR(36) PRIMARY KEY");
        assertThat(maria).contains("`created_at` TIMESTAMP(6) NOT NULL DEFAULT NOW()");
        assertThat(maria).contains("`id` BIGINT AUTO_INCREMENT PRIMARY KEY");
    }

    @Test
    void upsertPostgresVsMariadb() {
        AelionDb pg = AelionDb.preview(Dialect.POSTGRES);
        AelionDb maria = AelionDb.preview(Dialect.MARIADB);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String pgSql = pg.query().insert("ae_user")
                .value("uuid", id)
                .value("name", "Variiuz")
                .now("updated_at")
                .onConflict("uuid")
                .update("name")
                .updateNow("updated_at")
                .sql();
        String mariaSql = maria.query().insert("ae_user")
                .value("uuid", id)
                .value("name", "Variiuz")
                .now("updated_at")
                .onConflict("uuid")
                .update("name")
                .updateNow("updated_at")
                .sql();
        assertThat(pgSql).contains("ON CONFLICT (\"uuid\") DO UPDATE SET");
        assertThat(pgSql).contains("\"name\" = EXCLUDED.\"name\"");
        assertThat(mariaSql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(mariaSql).contains("`name` = VALUES(`name`)");
    }

    @Test
    void ilikeAndIgnoreCase() {
        AelionDb pg = AelionDb.preview(Dialect.POSTGRES);
        AelionDb maria = AelionDb.preview(Dialect.MARIADB);
        String pgSql = pg.query().select("uuid", "name").from("ae_user")
                .where(Where.ilike("target", "%abc%").and(Where.eqIgnoreCase("name", "Variiuz")))
                .orderBy("at desc")
                .limit(10)
                .offset(20)
                .sql();
        String mariaSql = maria.query().select("name").from("ae_user")
                .where(Where.ilike("target", "%abc%"))
                .sql();
        assertThat(pgSql).contains("\"target\" ILIKE ? ESCAPE '\\'");
        assertThat(pgSql).contains("LOWER(\"name\") = LOWER(?)");
        assertThat(pgSql).contains("ORDER BY \"at\" desc");
        assertThat(pgSql).contains("LIMIT 10 OFFSET 20");
        assertThat(mariaSql).contains("LOWER(`target`) LIKE LOWER(?) ESCAPE '\\'");
    }

    @Test
    void deleteAndSelectFilters() {
        AelionDb db = AelionDb.preview(Dialect.POSTGRES);
        assertThat(db.query().delete("ae_group").where(Where.eq("name", "default")).sql())
                .isEqualTo("DELETE FROM \"ae_group\" WHERE \"name\" = ?");
        assertThatThrownBy(() -> db.query().delete("ae_group").execute())
                .isInstanceOf(SqlException.class)
                .hasMessageContaining("WHERE");
        assertThat(db.query().select("name").from("ae_user")
                .where(Where.isNotNull("name").and(Where.ne("name", "")))
                .orderBy("name")
                .sql())
                .contains("\"name\" IS NOT NULL AND \"name\" <> ?");
    }

    @Test
    void cacheAside() {
        AelionDb db = AelionDb.preview(Dialect.POSTGRES, Duration.ofMinutes(5));
        NamedCache cache = db.cache("users");
        int[] loads = {0};
        String first = cache.get("a", () -> {
            loads[0]++;
            return "one";
        });
        String second = cache.get("a", () -> {
            loads[0]++;
            return "two";
        });
        assertThat(first).isEqualTo("one");
        assertThat(second).isEqualTo("one");
        assertThat(loads[0]).isEqualTo(1);
        cache.invalidate("a");
        assertThat(cache.get("a", () -> "three")).isEqualTo("three");
        cache.invalidateAll();
    }

    @Test
    void cacheDisabled() {
        AelionDb db = AelionDb.preview(Dialect.POSTGRES);
        assertThatThrownBy(() -> db.cache("users"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void previewCannotExecute() {
        AelionDb db = AelionDb.preview(Dialect.POSTGRES);
        assertThatThrownBy(() -> db.query().select("name").from("ae_user").list(rs -> rs.getString("name")))
                .isInstanceOf(SqlException.class);
    }

    @Test
    void insertWithoutConflictAndBuilder() {
        AelionDb db = AelionDb.preview(Dialect.MARIADB);
        assertThat(db.query().insert("ae_history")
                .value("actor", "console")
                .now("at")
                .sql())
                .isEqualTo("INSERT INTO `ae_history` (`actor`, `at`) VALUES (?, NOW())");
        assertThat(db.dialect()).isEqualTo(Dialect.MARIADB);
        db.close();
        AelionDb.Builder builder = AelionDb.builder()
                .url("jdbc:postgresql://localhost/aeperm")
                .user("aeperm")
                .password("aeperm")
                .maximumPoolSize(4)
                .minimumIdle(1)
                .poolName("test")
                .autoCommit(true)
                .cacheTtl(Duration.ofSeconds(30));
        assertThat(builder).isNotNull();
    }

    @Test
    void rowsAndBindHelpers() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("getObject".equals(method.getName())) {
                        return "uuid".equals(args[0]) ? id.toString() : id;
                    }
                    if ("getTimestamp".equals(method.getName())) {
                        return Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        assertThat(Rows.uuid(rs, "uuid")).isEqualTo(id);
        assertThat(Rows.uuid(rs, "other")).isEqualTo(id);
        assertThat(Rows.instant(rs, "at")).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> null
        );
        Dialect.POSTGRES.bind(ps, 1, id);
        Dialect.MARIADB.bind(ps, 1, id);
        Dialect.POSTGRES.bind(ps, 1, Instant.EPOCH);
        Dialect.POSTGRES.bind(ps, 1, "x");
        Dialect.POSTGRES.bindUuid(ps, 1, null);
        Col.varchar(8).nullable();
        assertThat(new SqlException("x")).hasMessage("x");
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

    private static String tableSql(Dialect dialect) {
        AelionDb db = AelionDb.preview(dialect);
        Table table = db.schema().table("ae_user")
                .column("uuid", Col.uuid().primaryKey())
                .column("name", Col.varchar(16))
                .column("weight", Col.integer().notNull())
                .column("nodes_json", Col.text().notNull())
                .column("created_at", Col.timestamp().defaultNow())
                .column("id", Col.identity())
                .index("ae_user_name_idx", "name");
        return table.ddl() + "; " + String.join("; ", table.indexDdl());
    }
}
