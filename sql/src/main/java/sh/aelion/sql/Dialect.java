package sh.aelion.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public enum Dialect {
    POSTGRES,
    MARIADB;

    public static Dialect fromUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("JDBC URL is required");
        }
        String normalized = url.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:postgresql:") || normalized.startsWith("jdbc:pgsql:")) {
            return POSTGRES;
        }
        if (normalized.startsWith("jdbc:mariadb:") || normalized.startsWith("jdbc:mysql:")) {
            return MARIADB;
        }
        throw new IllegalArgumentException("Unsupported JDBC URL (need postgresql or mariadb/mysql): " + url);
    }

    public String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier");
        }
        if (this == POSTGRES) {
            return "\"" + identifier.replace("\"", "\"\"") + "\"";
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    public String uuidType() {
        return this == POSTGRES ? "UUID" : "CHAR(36)";
    }

    public String timestampType() {
        return this == POSTGRES ? "TIMESTAMPTZ" : "TIMESTAMP(6)";
    }

    public String identityType() {
        return this == POSTGRES ? "BIGSERIAL" : "BIGINT AUTO_INCREMENT";
    }

    public String textType() {
        return "TEXT";
    }

    public String intType() {
        return "INT";
    }

    public String varcharType(int length) {
        return "VARCHAR(" + length + ")";
    }

    public String now() {
        return "NOW()";
    }

    public String ilike(String quotedColumn) {
        if (this == POSTGRES) {
            return quotedColumn + " ILIKE ? ESCAPE '\\'";
        }
        return "LOWER(" + quotedColumn + ") LIKE LOWER(?) ESCAPE '\\'";
    }

    public String eqIgnoreCase(String quotedColumn) {
        return "LOWER(" + quotedColumn + ") = LOWER(?)";
    }

    public String upsert(
            String quotedTable,
            String columnList,
            String valueList,
            String quotedPk,
            String updateAssignments
    ) {
        if (this == POSTGRES) {
            return "INSERT INTO " + quotedTable + " (" + columnList + ") VALUES (" + valueList + ")"
                    + " ON CONFLICT (" + quotedPk + ") DO UPDATE SET " + updateAssignments;
        }
        return "INSERT INTO " + quotedTable + " (" + columnList + ") VALUES (" + valueList + ")"
                + " ON DUPLICATE KEY UPDATE " + updateAssignments;
    }

    public String excluded(String quotedColumn) {
        if (this == POSTGRES) {
            return "EXCLUDED." + quotedColumn;
        }
        return "VALUES(" + quotedColumn + ")";
    }

    public void bind(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value instanceof UuidBind(UUID _uuid)) {
            bindUuid(ps, index, _uuid);
            return;
        }
        if (value instanceof Instant instant) {
            ps.setTimestamp(index, Timestamp.from(instant));
            return;
        }
        if (value instanceof UUID uuid) {
            bindUuid(ps, index, uuid);
            return;
        }
        if (value instanceof NowBind) {
            throw new SQLException("NOW() is not a bound parameter");
        }
        ps.setObject(index, value);
    }

    public void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (uuid == null) {
            ps.setObject(index, null);
            return;
        }
        if (this == POSTGRES) {
            ps.setObject(index, uuid);
        } else {
            ps.setString(index, uuid.toString());
        }
    }

    record UuidBind(UUID value) {
    }

    record NowBind() {
    }
}
