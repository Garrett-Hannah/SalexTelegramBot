package com.salex.telegram.infrastructure.messaging;

/**
 * Runtime exception thrown when a message cannot be persisted.
 */
public class MessagePersistenceException extends RuntimeException {
    public MessagePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
