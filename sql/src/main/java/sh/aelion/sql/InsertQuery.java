package sh.aelion.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InsertQuery {

    private static final Object NOW = new Object();

    private final AelionDb db;
    private final String table;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private String conflictColumn;
    private final List<String> updateColumns = new ArrayList<>();
    private final List<String> updateNowColumns = new ArrayList<>();

    InsertQuery(AelionDb db, String table) {
        this.db = db;
        this.table = table;
    }

    public InsertQuery value(String column, Object value) {
        values.put(column, value);
        return this;
    }

    public InsertQuery now(String column) {
        values.put(column, NOW);
        return this;
    }

    public InsertQuery onConflict(String primaryKey) {
        this.conflictColumn = primaryKey;
        return this;
    }

    public InsertQuery update(String... columns) {
        updateColumns.addAll(List.of(columns));
        return this;
    }

    public InsertQuery updateNow(String column) {
        updateNowColumns.add(column);
        return this;
    }

    public String sql() {
        Dialect dialect = db.dialect();
        String quotedTable = dialect.quote(table);
        List<String> cols = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            cols.add(dialect.quote(entry.getKey()));
            placeholders.add(entry.getValue() == NOW ? dialect.now() : "?");
        }
        String columnList = String.join(", ", cols);
        String valueList = String.join(", ", placeholders);
        if (conflictColumn == null) {
            return "INSERT INTO " + quotedTable + " (" + columnList + ") VALUES (" + valueList + ")";
        }
        List<String> assignments = new ArrayList<>();
        for (String column : updateColumns) {
            String quoted = dialect.quote(column);
            assignments.add(quoted + " = " + dialect.excluded(quoted));
        }
        for (String column : updateNowColumns) {
            assignments.add(dialect.quote(column) + " = " + dialect.now());
        }
        return dialect.upsert(
                quotedTable,
                columnList,
                valueList,
                dialect.quote(conflictColumn),
                String.join(", ", assignments)
        );
    }

    public int execute() {
        List<Object> bind = new ArrayList<>();
        for (Object value : values.values()) {
            if (value != NOW) {
                bind.add(value);
            }
        }
        try (PreparedStatement ps = db.connection().prepareStatement(sql())) {
            for (int i = 0; i < bind.size(); i++) {
                db.dialect().bind(ps, i + 1, bind.get(i));
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new SqlException(e);
        } finally {
            db.release();
        }
    }
}
