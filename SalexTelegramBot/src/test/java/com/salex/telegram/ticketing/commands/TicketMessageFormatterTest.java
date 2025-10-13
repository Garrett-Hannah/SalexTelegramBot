package com.salex.telegram.ticketing.commands;

import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketPriority;
import com.salex.telegram.ticketing.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketMessageFormatterTest {

    private final TicketMessageFormatter formatter = new TicketMessageFormatter();

    private final Ticket sampleTicket = Ticket.builder()
            .id(1L)
            .status(TicketStatus.OPEN)
            .priority(TicketPriority.LOW)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy(1L)
            .assignee(null)
            .summary("summary")
            .details("details")
            .build();

    @Test
    void creationPromptNotImplemented() {
        assertThatThrownBy(formatter::formatCreationPrompt)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ticketCardNotImplemented() {
        assertThatThrownBy(() -> formatter.formatTicketCard(sampleTicket))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void closurePromptNotImplemented() {
        assertThatThrownBy(() -> formatter.formatClosurePrompt(sampleTicket))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
