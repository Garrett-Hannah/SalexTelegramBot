package com.salex.telegram.prompting;

import com.salex.telegram.AIpackage.PromptModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Coordinates prompt session lifecycle and delegates message generation to the AI model.
 */
@Service
public class PromptService {

    private final PromptModel promptModel;
    private final PromptSessionStore sessionStore;

    public PromptService(PromptModel promptModel, PromptSessionStore sessionStore) {
        this.promptModel = promptModel;
        this.sessionStore = sessionStore;
    }

    /**
     * Creates a new prompt session for the given chat. Optionally seeds the conversation with a system primer.
     */
    public PromptSession startSession(long chatId, String systemPrimer) {
        if (sessionStore.find(chatId).isPresent()) {
            throw new IllegalStateException("Prompt session already active");
        }

        PromptSession session = new PromptSession(chatId);
        if (systemPrimer != null && !systemPrimer.isBlank()) {
            session.addSystemMessage(systemPrimer.trim());
        }
        sessionStore.save(session);
        return session;
    }

    public boolean hasActiveSession(long chatId) {
        return sessionStore.find(chatId).isPresent();
    }

    public ChatResponse sendUserMessage(long chatId, String message) {
        PromptSession session = sessionStore.find(chatId)
                .orElseThrow(() -> new IllegalStateException("No active prompt session"));

        String sanitized = sanitize(message);
        session.addUserMessage(sanitized);

        ChatResponse response = promptModel.call(session.toPrompt());
        session.addAssistantMessage(extractContent(response));

        sessionStore.save(session);
        return response;
    }

    public void closeSession(long chatId) {
        sessionStore.delete(chatId);
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        return message.trim();
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getContent();
    }
}
