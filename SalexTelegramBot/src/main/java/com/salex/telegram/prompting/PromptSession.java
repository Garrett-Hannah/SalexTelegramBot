package com.salex.telegram.prompting;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conversation envelope used while collecting user context before we hit the model.
 */
public class PromptSession {

    private final long chatId;
    private final Instant createdAt;
    private final List<Message> messages = new ArrayList<>();

    public PromptSession(long chatId) {
        this.chatId = chatId;
        this.createdAt = Instant.now();
    }

    public long getChatId() {
        return chatId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public Prompt toPrompt() {
        return new Prompt(messages);
    }

    public void addSystemMessage(String content) {
        messages.add(new SystemMessage(content));
    }

    public void addUserMessage(String content) {
        messages.add(new UserMessage(content));
    }

    public void addAssistantMessage(String content) {
        messages.add(new AssistantMessage(content));
    }
}
