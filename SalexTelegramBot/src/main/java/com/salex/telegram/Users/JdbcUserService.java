package com.salex.telegram.Users;

import com.salex.telegram.Database.ConnectionProvider;
import org.telegram.telegrambots.meta.api.objects.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed implementation that stores user metadata in the <code>users</code> table.
 */
public class JdbcUserService implements UserService {
    private final ConnectionProvider connectionProvider;

    public JdbcUserService(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public UserRecord ensureUser(User telegramUser) throws SQLException {
        Objects.requireNonNull(telegramUser, "telegramUser");
        long telegramId = telegramUser.getId();

        Optional<UserRecord> existing = findByTelegramId(telegramId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Connection connection = connectionProvider.getConnection();
        try (PreparedStatement insertUser = connection.prepareStatement(
                "INSERT INTO users (telegram_id, username, first_name, last_name) " +
                        "VALUES (?,?,?,?) RETURNING id, telegram_id, username, first_name, last_name")) {
            insertUser.setLong(1, telegramId);
            insertUser.setString(2, telegramUser.getUserName());
            insertUser.setString(3, telegramUser.getFirstName());
            insertUser.setString(4, telegramUser.getLastName());
            try (ResultSet rs = insertUser.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Failed to insert user for telegram id " + telegramId);
                }
                return mapRow(rs);
            }
        }
    }

    @Override
    public Optional<UserRecord> findByTelegramId(long telegramId) throws SQLException {
        Connection connection = connectionProvider.getConnection();
        try (PreparedStatement findUser = connection.prepareStatement(
                "SELECT id, telegram_id, username, first_name, last_name FROM users WHERE telegram_id=?")) {
            findUser.setLong(1, telegramId);
            try (ResultSet rs = findUser.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private UserRecord mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long telegramId = rs.getLong("telegram_id");
        String username = rs.getString("username");
        String firstName = rs.getString("first_name");
        String lastName = rs.getString("last_name");
        return new UserRecord(id, telegramId, username, firstName, lastName);
    }
}
