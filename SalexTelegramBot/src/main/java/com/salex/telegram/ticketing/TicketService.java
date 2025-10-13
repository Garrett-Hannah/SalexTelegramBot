package com.salex.telegram.ticketing;

import java.util.List;
import java.util.Optional;

/**
 * Coordinates validation, persistence and command messaging for tickets.
 */
public class TicketService {
    private final TicketRepository repository;
    private final TicketSessionManager sessionManager;

    public TicketService(TicketRepository repository, TicketSessionManager sessionManager) {
        this.repository = repository;
        this.sessionManager = sessionManager;
    }

    public Ticket startTicketCreation(long chatId, long userId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Ticket collectTicketField(long chatId, long userId, String messageText) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Optional<Ticket> getTicket(long ticketId, long userId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public List<Ticket> listTicketsForUser(long userId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Ticket closeTicket(long ticketId, long userId, String resolutionNote) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
