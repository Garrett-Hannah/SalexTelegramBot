package com.salex.telegram.conversation;

import com.salex.telegram.infrastructure.messaging.LoggedMessage;
import com.salex.telegram.infrastructure.messaging.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Maintains a short-term conversational buffer per chat/user pair, seeding from persisted history when
 * available so the bot can provide stateful responses without repeatedly querying the database.
 */
//TODO: does this need to be a service?
public class ConversationContextService {
    private static final Logger log = LoggerFactory.getLogger(ConversationContextService.class);
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private final MessageRepository messageRepository;
    private final int maxMessages;
    private final Map<ContextKey, ConversationHistory> histories = new ConcurrentHashMap<>();


    public ConversationContextService(MessageRepository messageRepository) {
        this(messageRepository, DEFAULT_MAX_MESSAGES);
    }

    public ConversationContextService(MessageRepository messageRepository, int maxMessages) {
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository");
        this.maxMessages = Math.max(2, maxMessages);
    }

    /**
     * Builds the message list to send to the LLM by combining cached dialogue with the new user input.
     * If no cache exists yet, the method lazily seeds it with the most recent persisted exchanges.
     *
     * @param chatId   telegram chat identifier
     * @param userId   internal user identifier
     * @param userText latest user prompt
     * @return ordered list of messages to send to the LLM API
     */
    public List<ConversationMessage> buildRequestMessages(long chatId, long userId, String userText) {
        Objects.requireNonNull(userText, "userText");

        ConversationHistory history = histories.computeIfAbsent(new ContextKey(chatId, userId), this::createHistory);
        history.seedIfNecessary(() -> loadFromRepository(chatId, userId));

        List<ConversationMessage> snapshot = history.snapshot();
        List<ConversationMessage> request = new ArrayList<>(snapshot.size() + 1);
        request.addAll(snapshot);
        request.add(new ConversationMessage("user", userText));
        log.debug("Prepared {} context messages ({} prior) for chat {}, user {}",
                request.size(),
                snapshot.size(),
                chatId,
                userId);
        return request;
    }

    /**
     * Records the completed exchange so future prompts can reference it without making another repository call.
     */
    public void recordExchange(long chatId, long userId, String userText, String assistantReply) {
        Objects.requireNonNull(userText, "userText");
        Objects.requireNonNull(assistantReply, "assistantReply");

        ConversationHistory history = histories.computeIfAbsent(new ContextKey(chatId, userId), this::createHistory);
        history.seedIfNecessary(() -> loadFromRepository(chatId, userId));
        history.append(new ConversationMessage("user", userText));
        history.append(new ConversationMessage("assistant", assistantReply));
        log.debug("Recorded exchange for chat {}, user {}; context now holds {} messages",
                chatId,
                userId,
                history.size());
    }

    /**
     * Clears any cached messages for the conversation.
     */
    public void resetConversation(long chatId, long userId) {
        histories.remove(new ContextKey(chatId, userId));
    }

    private ConversationHistory createHistory(ContextKey key) {
        return new ConversationHistory(maxMessages);
    }

    private List<ConversationMessage> loadFromRepository(long chatId, long userId) {
        List<LoggedMessage> stored;
        try {
            stored = messageRepository.findRecent(chatId, userId, maxMessages / 2);
        } catch (RuntimeException ex) {
            log.warn("Failed to load conversation history for chat {}, user {}: {}",
                    chatId, userId, ex.getMessage(), ex);
            return List.of();
        }
        if (stored.isEmpty()) {
            return List.of();
        }

        List<ConversationMessage> messages = new ArrayList<>(Math.min(maxMessages, stored.size() * 2));
        for (LoggedMessage loggedMessage : stored) {
            String request = loggedMessage.getRequestText();
            if (request != null && !request.isBlank()) {
                messages.add(new ConversationMessage("user", request));
            }

            String reply = loggedMessage.getReplyText();
            if (reply != null && !reply.isBlank()) {
                messages.add(new ConversationMessage("assistant", reply));
            }
        }

        int overflow = messages.size() - maxMessages;
        if (overflow > 0) {
            messages = messages.subList(overflow, messages.size());
        }

        log.debug("Loaded {} messages of conversation history for chat {}, user {}", messages.size(), chatId, userId);
        return List.copyOf(messages);
    }

    private record ContextKey(long chatId, long userId) {
    }

    private static final class ConversationHistory {
        private final int maxEntries;
        private final Deque<ConversationMessage> messages = new ArrayDeque<>();
        private boolean seeded;

        private ConversationHistory(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        private void seedIfNecessary(Supplier<List<ConversationMessage>> loader) {
            if (seeded) {
                return;
            }
            synchronized (this) {
                if (seeded) {
                    return;
                }
                if (loader != null) {
                    List<ConversationMessage> initial = loader.get();
                    if (initial != null && !initial.isEmpty()) {
                        for (ConversationMessage message : initial) {
                            appendInternal(message);
                        }
                    }
                }
                seeded = true;
            }
        }

        private List<ConversationMessage> snapshot() {
            synchronized (this) {
                return List.copyOf(messages);
            }
        }

        private void append(ConversationMessage message) {
            Objects.requireNonNull(message, "message");
            synchronized (this) {
                appendInternal(message);
            }
        }

        private void appendInternal(ConversationMessage message) {
            messages.addLast(message);
            while (messages.size() > maxEntries) {
                messages.removeFirst();
            }
        }

        private int size() {
            synchronized (this) {
                return messages.size();
            }
        }
    }
}
