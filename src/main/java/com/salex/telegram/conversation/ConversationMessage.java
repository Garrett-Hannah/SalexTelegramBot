package com.salex.telegram.conversation;

import java.util.Objects;

/**
 * Represents a single message exchanged in a conversation with the LLM.
 *
 * @param role    OpenAI chat role, e.g. {@code "user"} or {@code "assistant"}
 * @param content textual content sent with the role (never {@code null})
 */
public record ConversationMessage(String role, String content) {
    public ConversationMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }
}
