package com.salex.telegram.Ticketing;

/**
 * Lifecycle markers for a ticket. Expand as new states emerge.
 */
public enum TicketStatus {
    /**
     * Ticket has been created and awaits triage.
     */
    OPEN,
    /**
     * Work is actively being performed on the ticket.
     */
    IN_PROGRESS,
    /**
     * Ticket has been resolved and finalised.
     */
    CLOSED
}
