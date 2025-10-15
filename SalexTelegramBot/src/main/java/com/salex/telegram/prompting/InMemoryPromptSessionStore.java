package com.salex.telegram.prompting;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory backing store while we iterate on prompting behaviours.
 */
@Component
public class InMemoryPromptSessionStore implements PromptSessionStore {

    private final Map<Long, PromptSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<PromptSession> find(long chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }

    @Override
    public void save(PromptSession session) {
        sessions.put(session.getChatId(), session);
    }

    @Override
    public void delete(long chatId) {
        sessions.remove(chatId);
    }
}
