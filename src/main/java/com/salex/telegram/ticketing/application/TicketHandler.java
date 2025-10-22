package com.salex.telegram.ticketing.application;

import com.salex.telegram.ticketing.presentation.TicketMessageFormatter;

public class TicketHandler {
    private final TicketService ticketService;
    private final TicketMessageFormatter ticketFormatter;

    public TicketHandler(TicketService ticketService, TicketMessageFormatter ticketFormatter)
    {
        this.ticketService      = ticketService;
        this.ticketFormatter    = ticketFormatter;
    }
}
