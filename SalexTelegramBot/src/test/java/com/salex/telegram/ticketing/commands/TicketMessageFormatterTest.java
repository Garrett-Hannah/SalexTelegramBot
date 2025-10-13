package com.salex.telegram.ticketing.commands;

import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketDraft;
import com.salex.telegram.ticketing.TicketPriority;
import com.salex.telegram.ticketing.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
    void creationPromptMentionsTicketId() {
        assertThat(formatter.formatCreationPrompt(sampleTicket))
                .contains("Ticket #1")
                .contains("created");
    }

    @Test
    void ticketCardListsKeyFields() {
        String card = formatter.formatTicketCard(sampleTicket);
        assertThat(card).contains("Ticket #1");
        assertThat(card).contains("Status: OPEN");
        assertThat(card).contains("Summary: summary");
    }

    @Test
    void closurePromptAcknowledgesClose() {
        assertThat(formatter.formatClosurePrompt(sampleTicket))
                .contains("Closing ticket #1");
    }

    @Test
    void stepAcknowledgementReflectsStage() {
        Ticket updated = sampleTicket.toBuilder()
                .summary("New summary")
                .priority(TicketPriority.HIGH)
                .build();

        assertThat(formatter.formatStepAcknowledgement(TicketDraft.Step.SUMMARY, updated))
                .contains("New summary");
        assertThat(formatter.formatStepAcknowledgement(TicketDraft.Step.PRIORITY, updated))
                .contains("HIGH");
    }

    @Test
    void nextStepPromptGuidesUser() {
        assertThat(formatter.formatNextStepPrompt(TicketDraft.Step.PRIORITY))
                .contains("priority");
    }

    @Test
    void ticketListHandlesEmptyAndMultipleTickets() {
        assertThat(formatter.formatTicketList(java.util.List.of())).contains("no tickets");

        Ticket other = sampleTicket.toBuilder().id(42L).summary("Another").build();
        assertThat(formatter.formatTicketList(java.util.List.of(sampleTicket, other)))
                .contains("#1")
                .contains("#42");
    }

    @Test
    void helpExplainsCommands() {
        assertThat(formatter.formatHelp()).contains("/ticket new");
    }

    @Test
    void errorPrefixAdded() {
        assertThat(formatter.formatError("oops")).startsWith("[Error]");
    }

    @Test
    void creationCompleteIncludesCard() {
        assertThat(formatter.formatCreationComplete(sampleTicket))
                .contains("Ticket #1")
                .contains("Details: details");
    }
}
