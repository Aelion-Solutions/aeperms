package sh.aelion.sql;

import java.util.List;

public final class Query {

    private final AelionDb db;

    Query(AelionDb db) {
        this.db = db;
    }

    public SelectQuery select(String... columns) {
        return new SelectQuery(db, List.of(columns));
    }

    public InsertQuery insert(String table) {
        return new InsertQuery(db, table);
    }

    public DeleteQuery delete(String table) {
        return new DeleteQuery(db, table);
    }
}
