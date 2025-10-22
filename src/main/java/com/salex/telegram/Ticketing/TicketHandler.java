package com.salex.telegram.Ticketing;

import com.salex.telegram.Ticketing.commands.TicketMessageFormatter;

public class TicketHandler {
    private final TicketService ticketService;
    private final TicketMessageFormatter ticketFormatter;

    public TicketHandler(TicketService ticketService, TicketMessageFormatter ticketFormatter)
    {
        this.ticketService      = ticketService;
        this.ticketFormatter    = ticketFormatter;
    }
}
