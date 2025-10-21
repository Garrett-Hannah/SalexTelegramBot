package com.salex.telegram.Database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Supplies JDBC connections on demand, allowing callers to obtain a handle that is safe to use.
 */
public interface ConnectionProvider {

    /**
     * Returns a connection that is ready for use.
     *
     * @return open JDBC connection
     * @throws SQLException if a connection cannot be obtained
     */
    Connection getConnection() throws SQLException;
}
