package com.salex.telegram.Database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Creates new JDBC connections when invoked.
 */
@FunctionalInterface
public interface ConnectionFactory {
    Connection create() throws SQLException;
}
