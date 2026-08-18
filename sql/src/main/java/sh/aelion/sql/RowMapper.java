package sh.aelion.sql;

import java.sql.ResultSet;

@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws Exception;
}
