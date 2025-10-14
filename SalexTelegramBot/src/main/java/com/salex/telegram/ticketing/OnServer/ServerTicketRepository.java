package com.salex.telegram.ticketing.OnServer;

import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketPriority;
import com.salex.telegram.ticketing.TicketRepository;
import com.salex.telegram.ticketing.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed ticket repository that persists records to the configured database.
 */
public class ServerTicketRepository implements TicketRepository {
    private static final Logger log = LoggerFactory.getLogger(ServerTicketRepository.class);
    private static final String INSERT_SQL = """
            INSERT INTO tickets (status, priority, created_at, updated_at, created_by, assignee, summary, details)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, status, priority, created_at, updated_at, created_by, assignee, summary, details
            """;
    private static final String SELECT_BY_ID_SQL = """
            SELECT id, status, priority, created_at, updated_at, created_by, assignee, summary, details
            FROM tickets
            WHERE id = ?
            """;
    private static final String SELECT_FOR_USER_SQL = """
            SELECT id, status, priority, created_at, updated_at, created_by, assignee, summary, details
            FROM tickets
            WHERE created_by = ?
            ORDER BY id
            """;
    private static final String UPDATE_SQL = """
            UPDATE tickets
            SET status = ?, priority = ?, updated_at = ?, assignee = ?, summary = ?, details = ?
            WHERE id = ?
            """;

    private final Connection connection;

    /**
     * Creates a repository using the supplied JDBC connection.
     *
     * @param connection open JDBC connection
     */
    public ServerTicketRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        log.info("ServerTicketRepository initialised with JDBC connection");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Ticket createDraftTicket(Ticket draft) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
            bindTicket(ps, draft);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to insert ticket draft");
                }
                Ticket ticket = mapRow(rs);
                log.debug("Inserted ticket {} for user {}", ticket.getId(), ticket.getCreatedBy());
                return ticket;
            }
        } catch (SQLException ex) {
            log.error("Failed to create draft ticket: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to create draft ticket", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Ticket> findById(long ticketId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Ticket ticket = mapRow(rs);
                    log.debug("Loaded ticket {} for user {}", ticketId, ticket.getCreatedBy());
                    return Optional.of(ticket);
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            log.error("Failed to find ticket {}: {}", ticketId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to find ticket with id " + ticketId, ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Ticket> findAllForUser(long userId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_FOR_USER_SQL)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Ticket> tickets = new ArrayList<>();
                while (rs.next()) {
                    tickets.add(mapRow(rs));
                }
                log.debug("Fetched {} tickets for user {}", tickets.size(), userId);
                return tickets;
            }
        } catch (SQLException ex) {
            log.error("Failed to list tickets for user {}: {}", userId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to list tickets for user " + userId, ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Ticket save(Ticket ticket) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, ticket.getStatus().name());
            ps.setString(2, ticket.getPriority().name());
            ps.setTimestamp(3, Timestamp.from(ticket.getUpdatedAt()));
            if (ticket.getAssignee() == null) {
                ps.setNull(4, Types.BIGINT);
            } else {
                ps.setLong(4, ticket.getAssignee());
            }
            ps.setString(5, ticket.getSummary());
            ps.setString(6, ticket.getDetails());
            ps.setLong(7, ticket.getId());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException("Ticket not found or not updated: " + ticket.getId());
            }
            Ticket saved = findById(ticket.getId())
                    .orElseThrow(() -> new IllegalStateException("Ticket missing after update: " + ticket.getId()));
            log.debug("Updated ticket {}", ticket.getId());
            return saved;
        } catch (SQLException ex) {
            log.error("Failed to save ticket {}: {}", ticket.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to save ticket " + ticket.getId(), ex);
        }
    }

    /**
     * Binds mutable ticket properties to an insert statement.
     *
     * @param ps     prepared statement targeting the insert SQL
     * @param ticket ticket whose fields should be persisted
     * @throws SQLException if parameter binding fails
     */
    private void bindTicket(PreparedStatement ps, Ticket ticket) throws SQLException {
        ps.setString(1, ticket.getStatus().name());
        ps.setString(2, ticket.getPriority().name());
        ps.setTimestamp(3, Timestamp.from(ticket.getCreatedAt()));
        ps.setTimestamp(4, Timestamp.from(ticket.getUpdatedAt()));
        ps.setLong(5, ticket.getCreatedBy());
        if (ticket.getAssignee() == null) {
            ps.setNull(6, Types.BIGINT);
        } else {
            ps.setLong(6, ticket.getAssignee());
        }
        ps.setString(7, ticket.getSummary());
        ps.setString(8, ticket.getDetails());
    }

    /**
     * Converts the current result-set row into a {@link Ticket}.
     *
     * @param rs result set positioned on a ticket row
     * @return mapped ticket instance
     * @throws SQLException if column access fails
     */
    private Ticket mapRow(ResultSet rs) throws SQLException {
        Ticket.Builder builder = Ticket.builder()
                .id(rs.getLong("id"))
                .status(TicketStatus.valueOf(rs.getString("status")))
                .priority(TicketPriority.valueOf(rs.getString("priority")))
                .createdAt(toInstant(rs, "created_at"))
                .updatedAt(toInstant(rs, "updated_at"))
                .createdBy(rs.getLong("created_by"))
                .summary(rs.getString("summary"))
                .details(rs.getString("details"));

        long assignee = rs.getLong("assignee");
        if (!rs.wasNull()) {
            builder.assignee(assignee);
        }
        return builder.build();
    }

    /**
     * Safely extracts an {@link Instant} from a timestamp column.
     *
     * @param rs     result set containing the timestamp value
     * @param column column name to read
     * @return timestamp converted to an instant, or {@link Instant#now()} when {@code null}
     * @throws SQLException if column access fails
     */
    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : Instant.now();
    }
}
