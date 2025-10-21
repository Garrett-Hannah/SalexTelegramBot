package com.salex.telegram.messaging;

import com.salex.telegram.database.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.List;

/**
 * Persists bot conversations using a JDBC connection and can retrieve recent exchanges for context replay.
 */
public class JdbcMessageRepository implements MessageRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageRepository.class);
    private static final String INSERT_SQL =
            "INSERT INTO messages (user_id, chat_id, text, reply) VALUES (?,?,?,?)";
    private static final String SELECT_RECENT_SQL =
            "SELECT text, reply FROM messages WHERE chat_id=? AND user_id=? ORDER BY id DESC LIMIT ?";

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

    /**
     * Loads the most recent message pairs for the given chat/user, returning them oldest-first.
     */
    @Override
    public List<LoggedMessage> findRecent(long chatId, long userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<LoggedMessage> messages = new ArrayList<>();
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(SELECT_RECENT_SQL)) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("text");
                    String reply = rs.getString("reply");
                    LoggedMessage message = new LoggedMessage(
                            userId,
                            chatId,
                            text != null ? text : "",
                            reply != null ? reply : ""
                    );
                    messages.add(message);
                }
            }
        } catch (SQLException ex) {
            throw new MessagePersistenceException("Failed to load message history", ex);
        }

        Collections.reverse(messages);
        return List.copyOf(messages);
    }
}
