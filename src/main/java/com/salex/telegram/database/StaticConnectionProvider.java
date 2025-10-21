package com.salex.telegram.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Simple provider that always returns the same connection instance.
 */
public final class StaticConnectionProvider implements ConnectionProvider {
    private static final Logger log = LoggerFactory.getLogger(StaticConnectionProvider.class);
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

    @Override
    public void close() {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ex) {
            log.debug("Failed to close connection cleanly: {}", ex.getMessage());
        }
    }
}
