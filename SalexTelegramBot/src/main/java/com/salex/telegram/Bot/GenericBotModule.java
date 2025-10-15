package com.salex.telegram.Bot;

import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import org.slf4j.Logger;

//This module defines behaviour for default messages.
public class GenericBotModule implements TelegramBotModule{
    private static final Logger log = LoggerFactory.getLogger(GenericBotModule.class);

    GenericBotModule()

    @Override
    public boolean canHandle (Update update) {
        boolean valid = update.hasMessage();

    }

    @Override
    public void handle (Update update, SalexTelegramBot bot, long userId) {
        String userText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        long telegramId = update.getMessage().getFrom().getId();

        try {
            String replyText = bot.callChatGPT(userText); //TODO: Reimplement a way for this.
            log.info("ChatGPT responded to user {} with {} characters", userId, replyText.length());

            Connection conn = bot.getConnection(); //TODO: decide how i will get the connection ref.
            if (conn != null) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO messages (user_id, chat_id, text, reply) VALUES (?,?,?,?)");
                ps.setLong(1, userId);
                ps.setLong(2, chatId);
                ps.setString(3, userText);
                ps.setString(4, replyText);
                ps.executeUpdate();
                ps.close();
                log.debug("Persisted message for user {} in chat {}", userId, chatId);
            }

            bot.sendMessage(chatId, replyText);
        } catch (Exception e) {
            bot.sendMessage(chatId, "[Error] Failed to process message: " + e.getMessage());
            log.error("Failed to handle general message for user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, CommandHandler> getCommands () {
        return Map.of();
    }
}
