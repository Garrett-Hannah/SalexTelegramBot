package com.salex.telegram.Bot;

import com.salex.telegram.Messaging.LoggedMessage;
import com.salex.telegram.Messaging.MessageRepository;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;

//This module defines behaviour for default messages.
public class GenericBotModule implements TelegramBotModule{
    private static final Logger log = LoggerFactory.getLogger(GenericBotModule.class);
    private final MessageRepository messageRepository;

    GenericBotModule(MessageRepository messageRepository)
    {
        this.messageRepository = Objects.requireNonNull(messageRepository);
    }

    @Override
    public boolean canHandle (Update update) {
        return true;
    }

    @Override
    public void handle (Update update, SalexTelegramBot bot, long userId) {
        String userText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        Integer threadId = update.getMessage().getMessageThreadId();
        long telegramId = update.getMessage().getFrom().getId();

        try {
            bot.sendChatTyping(chatId, threadId);
            String replyText = bot.callChatGPT(userText); //TODO: Reimplement a way for this. shouldnt be public.
            log.info("ChatGPT responded to user {} with {} characters", userId, replyText.length());

            LoggedMessage loggedMessage =
                    new LoggedMessage(userId, chatId, userText, replyText);
            messageRepository.save(loggedMessage);
            bot.sendMessage(chatId, threadId, replyText);
        } catch (Exception e) {
            bot.sendMessage(chatId, threadId, "[Error] Failed to process message: " + e.getMessage());
            log.error("Failed to handle general message for user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, CommandHandler> getCommands () {
        return Map.of();
    }
}
