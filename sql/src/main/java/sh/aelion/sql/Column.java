package sh.aelion.sql;

public final class Column {

    enum Kind {
        UUID, VARCHAR, TEXT, INT, IDENTITY, TIMESTAMP
    }

    private final Kind kind;
    private String name;
    private int length;
    private boolean primaryKey;
    private boolean nullable = true;
    private boolean defaultNow;

    Column(Kind kind) {
        this.kind = kind;
    }

    Column length(int length) {
        this.length = length;
        return this;
    }

    public Column primaryKey() {
        this.primaryKey = true;
        this.nullable = false;
        return this;
    }

    public Column nullable() {
        this.nullable = true;
        return this;
    }

    public Column notNull() {
        this.nullable = false;
        return this;
    }

    public Column defaultNow() {
        this.defaultNow = true;
        this.nullable = false;
        return this;
    }

    void name(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    boolean isPrimaryKey() {
        return primaryKey;
    }

    String ddl(Dialect dialect) {
        StringBuilder sql = new StringBuilder();
        sql.append(dialect.quote(name)).append(' ');
        sql.append(switch (kind) {
            case UUID -> dialect.uuidType();
            case VARCHAR -> dialect.varcharType(length);
            case TEXT -> dialect.textType();
            case INT -> dialect.intType();
            case IDENTITY -> dialect.identityType();
            case TIMESTAMP -> dialect.timestampType();
        });
        if (primaryKey && kind != Kind.IDENTITY) {
            sql.append(" PRIMARY KEY");
        }
        if (kind == Kind.IDENTITY) {
            sql.append(" PRIMARY KEY");
        }
        if (!nullable && kind != Kind.IDENTITY && !primaryKey) {
            sql.append(" NOT NULL");
        }
        if (defaultNow) {
            sql.append(" DEFAULT ").append(dialect.now());
        }
        return sql.toString();
    }
}
