package sh.aelion.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class DeleteQuery {

    private final AelionDb db;
    private final String table;
    private Where where;

    DeleteQuery(AelionDb db, String table) {
        this.db = db;
        this.table = table;
    }

    public DeleteQuery where(Where where) {
        this.where = where;
        return this;
    }

    public String sql() {
        Dialect dialect = db.dialect();
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(dialect.quote(table));
        if (where != null) {
            sql.append(" WHERE ").append(where.flatten(dialect).sql());
        }
        return sql.toString();
    }

    public int execute() {
        if (where == null) {
            throw new SqlException("Refusing DELETE without WHERE");
        }
        Where.Rendered rendered = where.flatten(db.dialect());
        try (PreparedStatement ps = db.connection().prepareStatement(sql())) {
            for (int i = 0; i < rendered.bind().size(); i++) {
                db.dialect().bind(ps, i + 1, rendered.bind().get(i));
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new SqlException(e);
        } finally {
            db.release();
        }
    }
}
