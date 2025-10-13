package com.salex.telegram.ticketing.commands;

import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.ticketing.Ticket;

/**
 * Dispatches ticket-related commands invoked from Telegram into service calls.
 */
public class TicketCommandHandler {
    private final TicketService ticketService;

    public TicketCommandHandler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public void handleNewTicketCommand(long chatId, long userId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleTicketLookup(long chatId, long userId, long ticketId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleListTickets(long chatId, long userId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleAddNote(long chatId, long userId, long ticketId, String note) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleCloseTicket(long chatId, long userId, long ticketId, String confirmation) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
