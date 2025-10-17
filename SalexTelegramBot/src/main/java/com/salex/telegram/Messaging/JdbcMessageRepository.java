package com.salex.telegram.Messaging;

import com.salex.telegram.Database.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Persists bot conversations using a JDBC connection.
 */
public class JdbcMessageRepository implements MessageRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageRepository.class);
    private static final String INSERT_SQL =
            "INSERT INTO messages (user_id, chat_id, text, reply) VALUES (?,?,?,?)";

    private final ConnectionProvider connectionProvider;

    public JdbcMessageRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public void save(LoggedMessage message) {
        Objects.requireNonNull(message, "message");

        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(INSERT_SQL)) {
            ps.setLong(1, message.getUserId());
            ps.setLong(2, message.getChatId());
            ps.setString(3, message.getRequestText());
            ps.setString(4, message.getReplyText());
            ps.executeUpdate();
            log.debug("Persisted message for user {} in chat {}", message.getUserId(), message.getChatId());
        } catch (SQLException ex) {
            throw new MessagePersistenceException("Failed to persist message", ex);
        }
    }
}
