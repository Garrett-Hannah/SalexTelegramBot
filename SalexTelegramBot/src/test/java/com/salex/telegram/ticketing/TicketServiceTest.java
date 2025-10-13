package com.salex.telegram.ticketing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketServiceTest {

    private TicketRepository repository;
    private TicketSessionManager sessionManager;
    private TicketService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTicketRepository();
        sessionManager = new InMemoryTicketSessionManager();
        service = new TicketService(repository, sessionManager);
    }

    @Test
    void startTicketCreationPersistsDraftTicket() {
        Ticket ticket = service.startTicketCreation(100L, 7L);

        assertThat(ticket.getId()).isPositive();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(sessionManager.getDraft(100L, 7L))
                .isPresent()
                .get()
                .extracting(TicketDraft::getTicketId)
                .isEqualTo(ticket.getId());
    }

    @Test
    void collectTicketFieldAdvancesThroughSteps() {
        long chatId = 200L;
        long userId = 21L;
        Ticket ticket = service.startTicketCreation(chatId, userId);

        Ticket afterSummary = service.collectTicketField(chatId, userId, "API outage");
        assertThat(afterSummary.getSummary()).isEqualTo("API outage");

        Ticket afterPriority = service.collectTicketField(chatId, userId, "high");
        assertThat(afterPriority.getPriority()).isEqualTo(TicketPriority.HIGH);

        Ticket afterDetails = service.collectTicketField(chatId, userId, "Full repro steps");
        assertThat(afterDetails.getDetails()).isEqualTo("Full repro steps");

        assertThat(sessionManager.getDraft(chatId, userId)).isEmpty();
        assertThat(repository.findById(ticket.getId())).isPresent();
    }

    @Test
    void collectTicketFieldRejectsUnknownPriority() {
        long chatId = 99L;
        long userId = 5L;
        service.startTicketCreation(chatId, userId);
        service.collectTicketField(chatId, userId, "Printer offline");

        assertThatThrownBy(() -> service.collectTicketField(chatId, userId, "not-a-priority"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown priority");
    }

    @Test
    void getTicketRestrictsAccessToCreatorOrAssignee() {
        long chatId = 50L;
        long creatorId = 10L;
        Ticket ticket = service.startTicketCreation(chatId, creatorId);
        service.collectTicketField(chatId, creatorId, "Database issue");
        service.collectTicketField(chatId, creatorId, "medium");
        service.collectTicketField(chatId, creatorId, "Details here");

        assertThat(service.getTicket(ticket.getId(), creatorId)).isPresent();
        assertThat(service.getTicket(ticket.getId(), 999L)).isEmpty();

        repository.save(repository.findById(ticket.getId()).orElseThrow()
                .toBuilder()
                .assignee(999L)
                .build());

        assertThat(service.getTicket(ticket.getId(), 999L)).isPresent();
    }

    @Test
    void closeTicketMarksTicketClosedForAuthorisedUser() {
        long chatId = 70L;
        long creatorId = 11L;
        Ticket ticket = service.startTicketCreation(chatId, creatorId);
        service.collectTicketField(chatId, creatorId, "Bug");
        service.collectTicketField(chatId, creatorId, "low");
        service.collectTicketField(chatId, creatorId, "Steps");

        Ticket closed = service.closeTicket(ticket.getId(), creatorId, "Patched in v1.2");
        assertThat(closed.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(closed.getDetails()).contains("Resolution: Patched in v1.2");
    }

    @Test
    void closeTicketRejectsUnauthorisedUser() {
        long chatId = 88L;
        long creatorId = 15L;
        Ticket ticket = service.startTicketCreation(chatId, creatorId);
        service.collectTicketField(chatId, creatorId, "Security issue");
        service.collectTicketField(chatId, creatorId, "urgent");
        service.collectTicketField(chatId, creatorId, "Need patch");

        assertThatThrownBy(() -> service.closeTicket(ticket.getId(), 500L, "nope"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hasActiveDraftReportsState() {
        long chatId = 301L;
        long userId = 33L;

        assertThat(service.hasActiveDraft(chatId, userId)).isFalse();
        service.startTicketCreation(chatId, userId);
        assertThat(service.hasActiveDraft(chatId, userId)).isTrue();
        service.collectTicketField(chatId, userId, "Summary");
        service.collectTicketField(chatId, userId, "low");
        service.collectTicketField(chatId, userId, "Details");
        assertThat(service.hasActiveDraft(chatId, userId)).isFalse();
    }

    @Test
    void getActiveStepReturnsNextRequiredField() {
        long chatId = 302L;
        long userId = 34L;
        service.startTicketCreation(chatId, userId);

        assertThat(service.getActiveStep(chatId, userId)).contains(TicketDraft.Step.SUMMARY);
        service.collectTicketField(chatId, userId, "Issue");
        assertThat(service.getActiveStep(chatId, userId)).contains(TicketDraft.Step.PRIORITY);
        service.collectTicketField(chatId, userId, "medium");
        assertThat(service.getActiveStep(chatId, userId)).contains(TicketDraft.Step.DETAILS);
        service.collectTicketField(chatId, userId, "details here");
        assertThat(service.getActiveStep(chatId, userId)).isEmpty();
    }
}
