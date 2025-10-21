package com.salex.telegram.messaging;

import java.util.List;

/**
 * Message repository that intentionally discards all input and never returns stored history.
 */
public class NoopMessageRepository implements MessageRepository {
    @Override
    public void save(LoggedMessage message) {
        // intentionally no-op
    }

    @Override
    public List<LoggedMessage> findRecent(long chatId, long userId, int limit) {
        return List.of();
    }
}
