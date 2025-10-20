package com.salex.telegram.ticketing;

import com.salex.telegram.modules.ticketing.commands.TicketMessageFormatter;

public class TicketHandler {
    private final TicketService ticketService;
    private final TicketMessageFormatter ticketFormatter;

    public TicketHandler(TicketService ticketService, TicketMessageFormatter ticketFormatter)
    {
        this.ticketService      = ticketService;
        this.ticketFormatter    = ticketFormatter;
    }
}
