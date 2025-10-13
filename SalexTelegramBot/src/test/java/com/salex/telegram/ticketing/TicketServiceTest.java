package com.salex.telegram.ticketing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketServiceTest {

    private InMemoryTicketRepository repository;
    private InMemorySessionManager sessionManager;
    private TicketService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTicketRepository();
        sessionManager = new InMemorySessionManager();
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

    private static final class InMemoryTicketRepository implements TicketRepository {
        private final Map<Long, Ticket> store = new HashMap<>();
        private long sequence = 1L;

        @Override
        public synchronized Ticket createDraftTicket(Ticket draft) {
            long id = sequence++;
            Ticket persisted = draft.toBuilder()
                    .id(id)
                    .build();
            store.put(id, persisted);
            return persisted;
        }

        @Override
        public Optional<Ticket> findById(long ticketId) {
            return Optional.ofNullable(store.get(ticketId));
        }

        @Override
        public List<Ticket> findAllForUser(long userId) {
            return store.values().stream()
                    .filter(ticket -> ticket.getCreatedBy() == userId)
                    .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                    .toList();
        }

        @Override
        public Ticket save(Ticket ticket) {
            store.put(ticket.getId(), ticket);
            return ticket;
        }
    }

    private static final class InMemorySessionManager implements TicketSessionManager {
        private final Map<String, TicketDraft> sessions = new HashMap<>();

        @Override
        public void openSession(long chatId, long userId) {
            sessions.put(sessionKey(chatId, userId), new TicketDraft());
        }

        @Override
        public Optional<TicketDraft> getDraft(long chatId, long userId) {
            return Optional.ofNullable(sessions.get(sessionKey(chatId, userId)));
        }

        @Override
        public void updateDraft(long chatId, long userId, TicketDraft draft) {
            sessions.put(sessionKey(chatId, userId), draft);
        }

        @Override
        public void closeSession(long chatId, long userId) {
            sessions.remove(sessionKey(chatId, userId));
        }

        private String sessionKey(long chatId, long userId) {
            return chatId + ":" + userId;
        }
    }
}
