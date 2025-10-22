package com.salex.telegram.Ticketing;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for ticket persistence. Implementation will sit on top of JDBC or JPA.
 */
public interface TicketRepository {
    /**
     * Persists an initial ticket record in draft form.
     *
     * @param draft ticket data to store
     * @return the persisted ticket with generated identifiers
     */
    Ticket createDraftTicket(Ticket draft);

    /**
     * Finds a ticket by identifier.
     *
     * @param ticketId id of the ticket to locate
     * @return optional containing the ticket if present
     */
    Optional<Ticket> findById(long ticketId);

    /**
     * Lists all tickets owned by or associated with a user.
     *
     * @param userId user whose tickets should be returned
     * @return tickets accessible by the user
     */
    List<Ticket> findAllForUser(long userId);

    /**
     * Updates the stored representation of a ticket.
     *
     * @param ticket ticket instance with new data
     * @return the saved ticket
     */
    Ticket save(Ticket ticket);
}
