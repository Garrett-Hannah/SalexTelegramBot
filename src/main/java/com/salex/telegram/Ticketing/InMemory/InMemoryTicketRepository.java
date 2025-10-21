package com.salex.telegram.Ticketing.InMemory;

import com.salex.telegram.Ticketing.Ticket;
import com.salex.telegram.Ticketing.TicketRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory repository useful for local debugging and tests.
 */
public class InMemoryTicketRepository implements TicketRepository {
    private final ConcurrentHashMap<Long, Ticket> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);

    /**
     * {@inheritDoc}
     */
    @Override
    public Ticket createDraftTicket(Ticket draft) {
        long id = sequence.getAndIncrement();
        Ticket persisted = draft.toBuilder()
                .id(id)
                .build();
        store.put(id, persisted);
        return persisted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Ticket> findById(long ticketId) {
        return Optional.ofNullable(store.get(ticketId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Ticket> findAllForUser(long userId) {
        return store.values().stream()
                .filter(ticket -> ticket.getCreatedBy() == userId)
                .sorted(Comparator.comparingLong(Ticket::getId))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Ticket save(Ticket ticket) {
        store.put(ticket.getId(), ticket);
        return ticket;
    }
}
