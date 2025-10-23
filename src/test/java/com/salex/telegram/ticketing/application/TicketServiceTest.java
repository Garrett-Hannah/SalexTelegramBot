package com.salex.telegram.ticketing.application;

import com.salex.telegram.ticketing.domain.Ticket;
import com.salex.telegram.ticketing.domain.TicketDraft;
import com.salex.telegram.ticketing.domain.TicketPriority;
import com.salex.telegram.ticketing.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository repository;
    @Mock
    private TicketSessionManager sessionManager;

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(repository, sessionManager);
    }

    @Test
    void startTicketCreationCreatesDraftWhenNoSessionExists() {
        long chatId = 111L;
        long userId = 222L;
        when(sessionManager.getDraft(chatId, userId)).thenReturn(Optional.empty());

        Instant created = Instant.parse("2024-07-01T10:15:30Z");
        Ticket persisted = Ticket.builder()
                .id(10L)
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .createdBy(userId)
                .createdAt(created)
                .updatedAt(created)
                .summary("")
                .details("")
                .build();
        when(repository.createDraftTicket(any(Ticket.class))).thenReturn(persisted);

        Ticket result = service.startTicketCreation(chatId, userId);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(TicketStatus.OPEN);
        verify(sessionManager).openSession(chatId, userId);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(repository).createDraftTicket(ticketCaptor.capture());
        Ticket draft = ticketCaptor.getValue();
        assertThat(draft.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(draft.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(draft.getCreatedBy()).isEqualTo(userId);

        ArgumentCaptor<TicketDraft> draftCaptor = ArgumentCaptor.forClass(TicketDraft.class);
        verify(sessionManager).updateDraft(eq(chatId), eq(userId), draftCaptor.capture());
        assertThat(draftCaptor.getValue().getTicketId()).isEqualTo(10L);
    }

    @Test
    void startTicketCreationThrowsWhenDraftAlreadyExists() {
        long chatId = 1L;
        long userId = 2L;
        when(sessionManager.getDraft(chatId, userId)).thenReturn(Optional.of(new TicketDraft()));

        assertThrows(IllegalStateException.class, () -> service.startTicketCreation(chatId, userId));
        verifyNoInteractions(repository);
    }

    @Test
    void collectTicketFieldCapturesSummary() {
        long chatId = 5L;
        long userId = 6L;
        TicketDraft draft = new TicketDraft();
        draft.setTicketId(33L);
        when(sessionManager.getDraft(chatId, userId)).thenReturn(Optional.of(draft));

        Ticket existing = baseTicket(33L, userId);
        when(repository.findById(33L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket result = service.collectTicketField(chatId, userId, "  summary text  ");

        assertThat(result.getSummary()).isEqualTo("summary text");
        assertThat(draft.get(TicketDraft.Step.SUMMARY)).isEqualTo("summary text");
        verify(sessionManager, never()).closeSession(anyLong(), anyLong());
    }

    @Test
    void collectTicketFieldAppliesPriority() {
        long chatId = 8L;
        long userId = 9L;
        TicketDraft draft = new TicketDraft();
        draft.setTicketId(77L);
        draft.put(TicketDraft.Step.SUMMARY, "already captured");
        when(sessionManager.getDraft(chatId, userId)).thenReturn(Optional.of(draft));

        Ticket existing = baseTicket(77L, userId).toBuilder().summary("already captured").build();
        when(repository.findById(77L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket result = service.collectTicketField(chatId, userId, "high");

        assertThat(result.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(draft.get(TicketDraft.Step.PRIORITY)).isEqualTo("HIGH");
    }

    @Test
    void collectTicketFieldRejectsUnknownPriority() {
        long chatId = 3L;
        long userId = 4L;
        TicketDraft draft = new TicketDraft();
        draft.setTicketId(12L);
        draft.put(TicketDraft.Step.SUMMARY, "done");
        when(sessionManager.getDraft(chatId, userId)).thenReturn(Optional.of(draft));

        Ticket existing = baseTicket(12L, userId).toBuilder().summary("done").build();
        when(repository.findById(12L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.collectTicketField(chatId, userId, "not-a-priority"));
        verify(repository, never()).save(any());
        assertThat(draft.get(TicketDraft.Step.PRIORITY)).isNull();
    }

    @Test
    void collectTicketFieldStoresDetailsAndClosesSession() {
        long chatId = 44L;
        long userId = 55L;
        TicketDraft draft = new TicketDraft();
        draft.setTicketId(88L);
        draft.put(TicketDraft.Step.SUMMARY, "summary");
        draft.put(TicketDraft.Step.PRIORITY, "LOW");
        when(sessionManager.getDraft(chatId, userId)).thenReturn(Optional.of(draft));

        Ticket existing = baseTicket(88L, userId)
                .toBuilder()
                .summary("summary")
                .priority(TicketPriority.LOW)
                .build();
        when(repository.findById(88L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket result = service.collectTicketField(chatId, userId, "more details");

        assertThat(result.getDetails()).isEqualTo("more details");
        assertThat(draft.get(TicketDraft.Step.DETAILS)).isEqualTo("more details");
        verify(sessionManager).closeSession(chatId, userId);
    }

    @Test
    void getTicketReturnsOnlyWhenAuthorised() {
        long userId = 90L;
        Ticket owned = baseTicket(4L, userId);
        when(repository.findById(4L)).thenReturn(Optional.of(owned));

        assertThat(service.getTicket(4L, userId)).contains(owned);
        assertThat(service.getTicket(4L, 999L)).isEmpty();
    }

    @Test
    void listTicketsDelegatesToRepository() {
        long userId = 11L;
        List<Ticket> tickets = List.of(baseTicket(1L, userId), baseTicket(2L, userId));
        when(repository.findAllForUser(userId)).thenReturn(tickets);

        assertThat(service.listTicketsForUser(userId)).containsExactlyElementsOf(tickets);
    }

    @Test
    void closeTicketRejectsUnauthorisedUsers() {
        Ticket ticket = baseTicket(3L, 1L);
        when(repository.findById(3L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalStateException.class, () -> service.closeTicket(3L, 99L, ""));
        verify(repository, never()).save(any());
    }

    @Test
    void closeTicketUpdatesStatusAndAppendsResolution() {
        long userId = 700L;
        Ticket ticket = baseTicket(50L, userId)
                .toBuilder()
                .details("original details")
                .build();
        when(repository.findById(50L)).thenReturn(Optional.of(ticket));
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket closed = service.closeTicket(50L, userId, "fixed");

        assertThat(closed.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(closed.getDetails()).contains("original details")
                .contains("Resolution: fixed");
    }

    private Ticket baseTicket(long id, long userId) {
        Instant timestamp = Instant.parse("2024-07-01T12:00:00Z");
        return Ticket.builder()
                .id(id)
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .createdBy(userId)
                .summary("")
                .details("")
                .build();
    }
}
