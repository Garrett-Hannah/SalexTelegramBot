package com.salex.telegram.prompting;

import java.util.Optional;

/**
 * Persists prompt sessions so the bot can continue conversations across messages.
 */
public interface PromptSessionStore {

    Optional<PromptSession> find(long chatId);

    void save(PromptSession session);

    void delete(long chatId);
}
