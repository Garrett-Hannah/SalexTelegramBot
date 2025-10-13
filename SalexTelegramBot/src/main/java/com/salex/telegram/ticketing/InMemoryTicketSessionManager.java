package com.salex.telegram.ticketing;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session manager to track draft ticket creation flows.
 */
public class InMemoryTicketSessionManager implements TicketSessionManager {
    private final ConcurrentHashMap<String, TicketDraft> sessions = new ConcurrentHashMap<>();

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
