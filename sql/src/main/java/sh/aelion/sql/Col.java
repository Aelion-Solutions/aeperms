package sh.aelion.sql;

public final class Col {

    private Col() {
    }

    public static Column uuid() {
        return new Column(Column.Kind.UUID);
    }

    public static Column varchar(int length) {
        return new Column(Column.Kind.VARCHAR).length(length);
    }

    public static Column text() {
        return new Column(Column.Kind.TEXT);
    }

    public static Column integer() {
        return new Column(Column.Kind.INT);
    }

    public static Column identity() {
        return new Column(Column.Kind.IDENTITY).primaryKey();
    }

    public static Column timestamp() {
        return new Column(Column.Kind.TIMESTAMP);
    }
}
