package sh.aelion.sql;

public final class Schema {

    private final AelionDb db;

    Schema(AelionDb db) {
        this.db = db;
    }

    public Table table(String name) {
        return new Table(db, name);
    }
}
