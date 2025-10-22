package com.salex.telegram.application.modules;

import com.salex.telegram.conversation.ChatCompletionClient;
import com.salex.telegram.conversation.ConversationContextService;
import com.salex.telegram.conversation.ConversationMessage;
import com.salex.telegram.infrastructure.messaging.LoggedMessage;
import com.salex.telegram.infrastructure.messaging.MessageRepository;
import com.salex.telegram.telegram.SalexTelegramBot;
import com.salex.telegram.telegram.TelegramSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Map;

/**
 * Fallback conversational module that relays free-form chat to the LLM while persisting history.
 */
@Service
public class ConversationalRelayService implements MessagingHandlerService {
    private static final Logger log = LoggerFactory.getLogger(ConversationalRelayService.class);

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ConversationContextService conversationContextService;
    @Autowired
    private ChatCompletionClient chatCompletionClient;


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
