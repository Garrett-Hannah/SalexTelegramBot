package com.salex.telegram.ticketing.commands;

import com.salex.telegram.ticketing.TicketDraft;
import com.salex.telegram.ticketing.TicketRepository;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.ticketing.TicketSessionManager;
import com.salex.telegram.ticketing.Ticket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketCommandHandlerTest {

    private final TicketService service = new TicketService(
            new TicketRepository() {
                @Override
                public Ticket createDraftTicket(Ticket draft) {
                    throw new UnsupportedOperationException("stub");
                }

                @Override
                public Optional<Ticket> findById(long ticketId) {
                    return Optional.empty();
                }

                @Override
                public List<Ticket> findAllForUser(long userId) {
                    return List.of();
                }

                @Override
                public Ticket save(Ticket ticket) {
                    throw new UnsupportedOperationException("stub");
                }
            },
            new TicketSessionManager() {
                @Override
                public void openSession(long chatId, long userId) {
                    throw new UnsupportedOperationException("stub");
                }

                @Override
                public Optional<TicketDraft> getDraft(long chatId, long userId) {
                    return Optional.empty();
                }

                @Override
                public void updateDraft(long chatId, long userId, TicketDraft draft) {
                    throw new UnsupportedOperationException("stub");
                }

                @Override
                public void closeSession(long chatId, long userId) {
                    throw new UnsupportedOperationException("stub");
                }
            }
    );

    private final TicketCommandHandler handler = new TicketCommandHandler(service);

    @Test
    void newTicketHandlerNotImplemented() {
        assertThatThrownBy(() -> handler.handleNewTicketCommand(1L, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ticketLookupNotImplemented() {
        assertThatThrownBy(() -> handler.handleTicketLookup(1L, 1L, 2L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listTicketsNotImplemented() {
        assertThatThrownBy(() -> handler.handleListTickets(1L, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void addNoteNotImplemented() {
        assertThatThrownBy(() -> handler.handleAddNote(1L, 1L, 2L, "note"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void closeTicketNotImplemented() {
        assertThatThrownBy(() -> handler.handleCloseTicket(1L, 1L, 2L, "yes"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
