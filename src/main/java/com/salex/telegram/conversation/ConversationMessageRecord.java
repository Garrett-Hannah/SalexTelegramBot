package com.salex.telegram.conversation;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single message exchanged in a conversation with the LLM.
 *
 * @param role    OpenAI chat role, e.g. {@code "user"} or {@code "assistant"}
 * @param content textual content sent with the role (never {@code null})
 */
public record ConversationMessageRecord(String role, String content, Instant timestamp) {
    public ConversationMessageRecord {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public ConversationMessageRecord(String role, String content) {
        this(role, content, Instant.now());
    }


}
