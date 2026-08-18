package sh.aelion.sql;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Table {

    private final AelionDb db;
    private final String name;
    private final List<Column> columns = new ArrayList<>();
    private final List<Index> indexes = new ArrayList<>();

    Table(AelionDb db, String name) {
        this.db = db;
        this.name = name;
    }

    public Table column(String name, Column column) {
        column.name(name);
        columns.add(column);
        return this;
    }

    public Table index(String name, String... columnNames) {
        indexes.add(new Index(name, List.of(columnNames)));
        return this;
    }

    public String ddl() {
        Dialect dialect = db.dialect();
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(dialect.quote(name)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns.get(i).ddl(dialect));
        }
        sql.append(')');
        return sql.toString();
    }

    public List<String> indexDdl() {
        Dialect dialect = db.dialect();
        List<String> statements = new ArrayList<>();
        for (Index index : indexes) {
            String cols = String.join(", ", index.columns.stream().map(dialect::quote).toList());
            statements.add("CREATE INDEX IF NOT EXISTS " + dialect.quote(index.name)
                    + " ON " + dialect.quote(name) + " (" + cols + ")");
        }
        return statements;
    }

    public void create() {
        try (var connection = db.connection(); Statement statement = connection.createStatement()) {
            statement.execute(ddl());
            for (String indexSql : indexDdl()) {
                statement.execute(indexSql);
            }
        } catch (SQLException e) {
            throw new SqlException(e);
        } finally {
            db.release();
        }
    }

    private record Index(String name, List<String> columns) {
    }
}
