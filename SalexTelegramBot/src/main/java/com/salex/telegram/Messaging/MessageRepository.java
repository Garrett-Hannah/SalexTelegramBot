package com.salex.telegram.Messaging;

import java.util.List;

/**
 * Abstraction for persisting and retrieving conversational exchanges.
 */
public interface MessageRepository {
    void save(LoggedMessage message);

    /**
     * Retrieves the most recent persisted exchanges for the given chat/user pair, ordered oldest to newest.
     *
     * @param chatId the chat identifier conversations originate from
     * @param userId the internal user identifier
     * @param limit  maximum number of exchanges to return
     * @return ordered list of {@link LoggedMessage} instances oldest to newest; may be empty
     */
    List<LoggedMessage> findRecent(long chatId, long userId, int limit);
}
