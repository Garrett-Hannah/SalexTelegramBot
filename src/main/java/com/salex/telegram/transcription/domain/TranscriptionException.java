package com.salex.telegram.transcription.domain;

/**
 * Exception thrown when audio download or transcription fails.
 */
public class TranscriptionException extends RuntimeException {
    public TranscriptionException(String message) {
        super(message);
    }

    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
