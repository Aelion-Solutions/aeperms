package sh.aelion.sql;

import java.sql.SQLException;

public final class SqlException extends RuntimeException {

    public SqlException(String message) {
        super(message);
    }

    public SqlException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqlException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
