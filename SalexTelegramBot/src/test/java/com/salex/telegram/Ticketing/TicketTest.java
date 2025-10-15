package com.salex.telegram.Ticketing;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    @Test
    void builderCreatesImmutableTicket() {
        Instant now = Instant.now();
        Ticket ticket = Ticket.builder()
                .id(123L)
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.HIGH)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(42L)
                .assignee(99L)
                .summary("API outage")
                .details("Service returns 500")
                .build();

        assertThat(ticket.getId()).isEqualTo(123L);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(ticket.getSummary()).isEqualTo("API outage");
        assertThat(ticket.getDetails()).isEqualTo("Service returns 500");
        assertThat(ticket.getAssignee()).isEqualTo(99L);
    }

    @Test
    void toBuilderProducesCopy() {
        Instant now = Instant.now();
        Ticket original = Ticket.builder()
                .id(1L)
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(7L)
                .assignee(null)
                .summary("Original")
                .details("Original details")
                .build();

        Ticket modified = original.toBuilder()
                .summary("Updated summary")
                .build();

        assertThat(modified.getSummary()).isEqualTo("Updated summary");
        assertThat(modified.getDetails()).isEqualTo("Original details");
        assertThat(modified.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void builderRequiresMandatoryFields() {
        assertThatThrownBy(() -> Ticket.builder().build())
                .isInstanceOf(NullPointerException.class);
    }
}
