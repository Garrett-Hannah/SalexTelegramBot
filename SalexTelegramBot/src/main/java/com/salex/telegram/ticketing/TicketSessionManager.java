package com.salex.telegram.ticketing;

import java.util.Optional;

/**
 * Tracks in-progress ticket creation flows per chat/user pair.
 */
public interface TicketSessionManager {
    void openSession(long chatId, long userId);

    Optional<TicketDraft> getDraft(long chatId, long userId);

    void updateDraft(long chatId, long userId, TicketDraft draft);

    void closeSession(long chatId, long userId);
}
