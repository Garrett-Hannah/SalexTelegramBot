package com.salex.telegram.ticketing.application;

import com.salex.telegram.ticketing.domain.TicketDraft;

import java.util.Optional;

/**
 * Tracks in-progress ticket creation flows per chat/user pair.
 */
public interface TicketSessionManager {
    /**
     * Marks a new ticket creation session as active.
     *
     * @param chatId chat identifier scoping the session
     * @param userId internal user identifier
     */
    void openSession(long chatId, long userId);

    /**
     * Retrieves the current draft for a chat/user combination.
     *
     * @param chatId chat identifier scoping the session
     * @param userId internal user identifier
     * @return optional draft if a session is active
     */
    Optional<TicketDraft> getDraft(long chatId, long userId);

    /**
     * Stores the updated draft contents for an ongoing session.
     *
     * @param chatId chat identifier scoping the session
     * @param userId internal user identifier
     * @param draft  draft to persist
     */
    void updateDraft(long chatId, long userId, TicketDraft draft);

    /**
     * Terminates the session for the given chat and user.
     *
     * @param chatId chat identifier scoping the session
     * @param userId internal user identifier
     */
    void closeSession(long chatId, long userId);
}
