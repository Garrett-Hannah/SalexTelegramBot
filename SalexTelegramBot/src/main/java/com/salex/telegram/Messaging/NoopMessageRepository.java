package com.salex.telegram.Messaging;

/**
 * Message repository that intentionally discards all input.
 */
public class NoopMessageRepository implements MessageRepository {
    @Override
    public void save(LoggedMessage message) {
        // intentionally no-op
    }
}
