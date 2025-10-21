package com.salex.telegram.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Provides a single JDBC connection that is transparently re-established when it becomes invalid or closed.
 */
public final class RefreshingConnectionProvider implements ConnectionProvider, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(RefreshingConnectionProvider.class);

    private final ConnectionFactory factory;
    private final int validationTimeoutSeconds;
    private final Object lock = new Object();

    private volatile Connection current;

    public RefreshingConnectionProvider(ConnectionFactory factory, int validationTimeoutSeconds) {
        this.factory = Objects.requireNonNull(factory, "factory");
        if (validationTimeoutSeconds < 0) {
            throw new IllegalArgumentException("validationTimeoutSeconds must be >= 0");
        }
        this.validationTimeoutSeconds = validationTimeoutSeconds;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection existing = current;
        if (isUsable(existing)) {
            return existing;
        }
        synchronized (lock) {
            existing = current;
            if (!isUsable(existing)) {
                reconnect();
            }
            return current;
        }
    }

    private boolean isUsable(Connection connection) {
        if (connection == null) {
            return false;
        }
        try {
            return !connection.isClosed() && connection.isValid(validationTimeoutSeconds);
        } catch (SQLException ex) {
            log.debug("Connection validation failed: {}", ex.getMessage());
            return false;
        }
    }

    private void reconnect() throws SQLException {
        closeQuietly(current);
        current = factory.create();
        log.info("Re-established JDBC connection");
    }

    @Override
    public void close() {
        synchronized (lock) {
            closeQuietly(current);
            current = null;
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            log.debug("Failed to close connection cleanly: {}", ex.getMessage());
        }
    }
}
