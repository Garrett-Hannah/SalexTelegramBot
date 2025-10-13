package com.salex.telegram.ticketing;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for ticket persistence. Implementation will sit on top of JDBC or JPA.
 */
public interface TicketRepository {
    Ticket createDraftTicket(Ticket draft);

    Optional<Ticket> findById(long ticketId);

    List<Ticket> findAllForUser(long userId);

    Ticket save(Ticket ticket);
}
