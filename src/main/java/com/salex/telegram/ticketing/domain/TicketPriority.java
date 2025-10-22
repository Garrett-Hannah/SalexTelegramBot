package com.salex.telegram.ticketing.domain;

/**
 * Priority levels for handling response expectations.
 */
public enum TicketPriority {
    /**
     * Low urgency, can be addressed at convenience.
     */
    LOW,
    /**
     * Medium urgency, requires timely attention.
     */
    MEDIUM,
    /**
     * High urgency, should be prioritised over routine work.
     */
    HIGH,
    /**
     * Critical urgency, needs immediate intervention.
     */
    URGENT
}
