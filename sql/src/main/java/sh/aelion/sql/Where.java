package sh.aelion.sql;

import java.util.ArrayList;
import java.util.List;

public final class Where {

    private final List<Clause> clauses;

    private Where(List<Clause> clauses) {
        this.clauses = clauses;
    }

    public static Where eq(String column, Object value) {
        return new Where(List.of(new Clause(Op.EQ, column, value)));
    }

    public static Where ne(String column, Object value) {
        return new Where(List.of(new Clause(Op.NE, column, value)));
    }

    public static Where isNotNull(String column) {
        return new Where(List.of(new Clause(Op.NOT_NULL, column, null)));
    }

    public static Where ilike(String column, String pattern) {
        return new Where(List.of(new Clause(Op.ILIKE, column, pattern)));
    }

    public static Where eqIgnoreCase(String column, String value) {
        return new Where(List.of(new Clause(Op.EQ_IC, column, value)));
    }

    public Where and(Where other) {
        List<Clause> merged = new ArrayList<>(clauses);
        merged.addAll(other.clauses);
        return new Where(List.copyOf(merged));
    }

    Rendered flatten(Dialect dialect) {
        List<String> fragments = new ArrayList<>();
        List<Object> bind = new ArrayList<>();
        for (Clause clause : clauses) {
            String quoted = dialect.quote(clause.column);
            switch (clause.op) {
                case EQ -> {
                    fragments.add(quoted + " = ?");
                    bind.add(clause.value);
                }
                case NE -> {
                    fragments.add(quoted + " <> ?");
                    bind.add(clause.value);
                }
                case NOT_NULL -> fragments.add(quoted + " IS NOT NULL");
                case ILIKE -> {
                    fragments.add(dialect.ilike(quoted));
                    bind.add(clause.value);
                }
                case EQ_IC -> {
                    fragments.add(dialect.eqIgnoreCase(quoted));
                    bind.add(clause.value);
                }
            }
        }
        return new Rendered(String.join(" AND ", fragments), bind);
    }

    private enum Op {
        EQ, NE, NOT_NULL, ILIKE, EQ_IC
    }

    private record Clause(Op op, String column, Object value) {
    }

    record Rendered(String sql, List<Object> bind) {
    }
}
