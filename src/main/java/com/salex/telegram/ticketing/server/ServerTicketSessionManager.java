package com.salex.telegram.ticketing.server;

import com.salex.telegram.database.ConnectionProvider;
import com.salex.telegram.ticketing.TicketDraft;
import com.salex.telegram.ticketing.TicketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed session manager that persists draft progress per chat/user pair.
 */
public class ServerTicketSessionManager implements TicketSessionManager {
    private static final Logger log = LoggerFactory.getLogger(ServerTicketSessionManager.class);
    private static final String INSERT_SESSION_SQL = """
            INSERT INTO ticket_sessions (chat_id, user_id, ticket_id, summary, priority, details)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String DELETE_SESSION_SQL = """
            DELETE FROM ticket_sessions
            WHERE chat_id = ? AND user_id = ?
            """;
    private static final String SELECT_SESSION_SQL = """
            SELECT ticket_id, summary, priority, details
            FROM ticket_sessions
            WHERE chat_id = ? AND user_id = ?
            """;
    private static final String UPDATE_SESSION_SQL = """
            UPDATE ticket_sessions
            SET ticket_id = ?, summary = ?, priority = ?, details = ?
            WHERE chat_id = ? AND user_id = ?
            """;

    private final ConnectionProvider connectionProvider;

    /**
     * Creates a session manager bound to the given JDBC connection provider.
     *
     * @param connectionProvider provider supplying JDBC connections
     */
    public ServerTicketSessionManager(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        log.info("ServerTicketSessionManager initialised with JDBC connection provider");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void openSession(long chatId, long userId) {
        try {
            closeSession(chatId, userId);
            try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(INSERT_SESSION_SQL)) {
                ps.setLong(1, chatId);
                ps.setLong(2, userId);
                ps.setNull(3, Types.BIGINT);
                ps.setNull(4, Types.VARCHAR);
                ps.setNull(5, Types.VARCHAR);
                ps.setNull(6, Types.VARCHAR);
                ps.executeUpdate();
                log.info("Opened ticket session for chat {}, user {}", chatId, userId);
            }
        } catch (SQLException ex) {
            log.error("Failed to open session for chat {}, user {}: {}", chatId, userId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to open ticket session", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TicketDraft> getDraft(long chatId, long userId) {
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(SELECT_SESSION_SQL)) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                return Optional.empty();
            }
            TicketDraft draft = new TicketDraft();
            Long ticketId = rs.getObject("ticket_id", Long.class);
            if (ticketId != null) {
                draft.setTicketId(ticketId);
            }
                String summary = rs.getString("summary");
                if (summary != null) {
                    draft.put(TicketDraft.Step.SUMMARY, summary);
                }
                String priority = rs.getString("priority");
                if (priority != null) {
                    draft.put(TicketDraft.Step.PRIORITY, priority);
                }
                String details = rs.getString("details");
                if (details != null) {
                    draft.put(TicketDraft.Step.DETAILS, details);
                }
                log.debug("Loaded ticket session for chat {}, user {}", chatId, userId);
                return Optional.of(draft);
            }
        } catch (SQLException ex) {
            log.error("Failed to fetch session for chat {}, user {}: {}", chatId, userId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch ticket session", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateDraft(long chatId, long userId, TicketDraft draft) {
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(UPDATE_SESSION_SQL)) {
            if (draft.getTicketId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, draft.getTicketId());
            }
            setNullableString(ps, 2, draft.get(TicketDraft.Step.SUMMARY));
            setNullableString(ps, 3, draft.get(TicketDraft.Step.PRIORITY));
            setNullableString(ps, 4, draft.get(TicketDraft.Step.DETAILS));
            ps.setLong(5, chatId);
            ps.setLong(6, userId);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                openSession(chatId, userId);
                updateDraft(chatId, userId, draft);
            } else {
                log.debug("Updated ticket session for chat {}, user {}", chatId, userId);
            }
        } catch (SQLException ex) {
            log.error("Failed to update session for chat {}, user {}: {}", chatId, userId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to update ticket session", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeSession(long chatId, long userId) {
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(DELETE_SESSION_SQL)) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            ps.executeUpdate();
            log.info("Closed ticket session for chat {}, user {}", chatId, userId);
        } catch (SQLException ex) {
            log.error("Failed to close session for chat {}, user {}: {}", chatId, userId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to close ticket session", ex);
        }
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
