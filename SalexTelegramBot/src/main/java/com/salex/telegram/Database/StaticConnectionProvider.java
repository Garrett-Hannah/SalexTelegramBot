package com.salex.telegram.Database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Simple provider that always returns the same connection instance.
 */
public final class StaticConnectionProvider implements ConnectionProvider {
    private final Connection connection;

    public StaticConnectionProvider(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (connection.isClosed()) {
            throw new SQLException("Connection is closed");
        }
        return connection;
    }
}
