package com.salex.telegram.messaging;

import java.util.Objects;

/**
 * Value object describing a user interaction and the generated reply.
 */
public final class LoggedMessage {
    private final long userId;
    private final long chatId;
    private final String requestText;
    private final String replyText;

    public LoggedMessage(long userId, long chatId, String requestText, String replyText) {
        this.userId = userId;
        this.chatId = chatId;
        this.requestText = Objects.requireNonNull(requestText, "requestText");
        this.replyText = Objects.requireNonNull(replyText, "replyText");
    }

    public long getUserId() {
        return userId;
    }

    public long getChatId() {
        return chatId;
    }

    public String getRequestText() {
        return requestText;
    }

    public String getReplyText() {
        return replyText;
    }
}
