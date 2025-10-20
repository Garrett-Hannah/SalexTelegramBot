package com.salex.telegram.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Creates new JDBC connections when invoked.
 */
@FunctionalInterface
public interface ConnectionFactory {
    Connection create() throws SQLException;
}
