package com.salex.telegram.modules;

import com.salex.telegram.AiPackage.ChatCompletionClient;
import com.salex.telegram.AiPackage.ConversationContextService;
import com.salex.telegram.AiPackage.ConversationMessage;
import com.salex.telegram.Bot.SalexTelegramBot;
import com.salex.telegram.Messaging.LoggedMessage;
import com.salex.telegram.Messaging.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fallback conversational module that relays free-form chat to the LLM while persisting history.
 */
public class ConversationalRelayModule implements TelegramBotModule {
    private static final Logger log = LoggerFactory.getLogger(ConversationalRelayModule.class);
    private final MessageRepository messageRepository;
    private final ConversationContextService conversationContextService;
    private final ChatCompletionClient chatCompletionClient;

    ConversationalRelayModule(MessageRepository messageRepository,
                              ConversationContextService conversationContextService,
                              ChatCompletionClient chatCompletionClient) {
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.conversationContextService = Objects.requireNonNull(conversationContextService);
        this.chatCompletionClient = Objects.requireNonNull(chatCompletionClient);
    }

    @Override
    public boolean canHandle(Update update, long userId) {
        return true;
    }

    /**
     * Forwards the latest user message to the language model, including prior context, then
     * logs the exchange and replies back to Telegram.
     */
    @Override
    public void handle(Update update, SalexTelegramBot bot, long userId) {
        if (update == null || !update.hasMessage() || !update.getMessage().hasText()) {
            log.debug("ConversationalRelayModule skipped non-text update for user {}", userId);
            return;
        }

        String userText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        Integer threadId = update.getMessage().getMessageThreadId();

        try {
            bot.sendChatTyping(chatId, threadId);

            List<ConversationMessage> requestMessages =
                    conversationContextService.buildRequestMessages(chatId, userId, userText);
            String replyText = chatCompletionClient.complete(requestMessages);
            log.info("ChatGPT responded to user {} with {} characters", userId, replyText.length());

            conversationContextService.recordExchange(chatId, userId, userText, replyText);

            LoggedMessage loggedMessage = new LoggedMessage(userId, chatId, userText, replyText);
            messageRepository.save(loggedMessage);
            bot.sendMessage(chatId, threadId, replyText);
        } catch (Exception e) {
            bot.sendMessage(chatId, threadId, "[Error] Failed to process message: " + e.getMessage());
            log.error("Failed to handle general message for user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, CommandHandler> getCommands() {
        return Map.of();
    }
}
