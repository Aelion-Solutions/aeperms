package sh.aelion.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SelectQuery {

    private final AelionDb db;
    private final List<String> columns;
    private String table;
    private Where where;
    private String orderBy;
    private Integer limit;
    private Integer offset;

    SelectQuery(AelionDb db, List<String> columns) {
        this.db = db;
        this.columns = List.copyOf(columns);
    }

    public SelectQuery from(String table) {
        this.table = table;
        return this;
    }

    public SelectQuery where(Where where) {
        this.where = where;
        return this;
    }

    public SelectQuery orderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public SelectQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SelectQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    public String sql() {
        Dialect dialect = db.dialect();
        String cols = columns.isEmpty() || (columns.size() == 1 && "*".equals(columns.getFirst()))
                ? "*"
                : columns.stream().map(dialect::quote).collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(" FROM ").append(dialect.quote(table));
        List<Object> ignored = new ArrayList<>();
        appendWhere(sql, ignored);
        if (orderBy != null && !orderBy.isBlank()) {
            sql.append(" ORDER BY ").append(quoteOrder(dialect, orderBy));
        }
        if (limit != null) {
            sql.append(" LIMIT ").append(Math.max(limit, 0));
        }
        if (offset != null) {
            sql.append(" OFFSET ").append(Math.max(offset, 0));
        }
        return sql.toString();
    }

    public <T> Optional<T> one(RowMapper<T> mapper) {
        List<T> rows = list(mapper);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public <T> List<T> list(RowMapper<T> mapper) {
        List<Object> bind = new ArrayList<>();
        String sql = sql();
        if (where != null) {
            bind.addAll(where.flatten(db.dialect()).bind());
        }
        List<T> rows = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            bindAll(ps, bind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
        } catch (Exception e) {
            throw wrap(e);
        } finally {
            db.release();
        }
        return rows;
    }

    private void appendWhere(StringBuilder sql, List<Object> bind) {
        if (where == null) {
            return;
        }
        Where.Rendered rendered = where.flatten(db.dialect());
        sql.append(" WHERE ").append(rendered.sql());
        bind.addAll(rendered.bind());
    }

    private static String quoteOrder(Dialect dialect, String orderBy) {
        String trimmed = orderBy.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return dialect.quote(trimmed);
        }
        return dialect.quote(trimmed.substring(0, space)) + trimmed.substring(space);
    }

    private void bindAll(PreparedStatement ps, List<Object> bind) throws SQLException {
        for (int i = 0; i < bind.size(); i++) {
            db.dialect().bind(ps, i + 1, bind.get(i));
        }
    }

    private static SqlException wrap(Exception e) {
        if (e instanceof SqlException sql) {
            return sql;
        }
        if (e instanceof SQLException sql) {
            return new SqlException(sql);
        }
        return new SqlException(e.getMessage(), e);
    }
}
