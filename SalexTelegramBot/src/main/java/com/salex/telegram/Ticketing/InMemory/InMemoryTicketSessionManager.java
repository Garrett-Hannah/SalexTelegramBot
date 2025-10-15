package com.salex.telegram.Ticketing.InMemory;

import com.salex.telegram.Ticketing.TicketDraft;
import com.salex.telegram.Ticketing.TicketSessionManager;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session manager to track draft ticket creation flows.
 */
public class InMemoryTicketSessionManager implements TicketSessionManager {
    private final ConcurrentHashMap<String, TicketDraft> sessions = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void openSession(long chatId, long userId) {
        sessions.put(sessionKey(chatId, userId), new TicketDraft());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TicketDraft> getDraft(long chatId, long userId) {
        return Optional.ofNullable(sessions.get(sessionKey(chatId, userId)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateDraft(long chatId, long userId, TicketDraft draft) {
        sessions.put(sessionKey(chatId, userId), draft);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeSession(long chatId, long userId) {
        sessions.remove(sessionKey(chatId, userId));
    }

    /**
     * Builds a unique session key for storing draft data.
     *
     * @param chatId chat identifier
     * @param userId user identifier
     * @return composite session key
     */
    private String sessionKey(long chatId, long userId) {
        return chatId + ":" + userId;
    }
}
