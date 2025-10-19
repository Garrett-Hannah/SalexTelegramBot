package com.salex.telegram.Bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Small helper that encapsulates Telegram send/typing operations.
 */
final class TelegramSender {
    private static final Logger log = LoggerFactory.getLogger(TelegramSender.class);
    private final TelegramLongPollingBot bot;

    TelegramSender(TelegramLongPollingBot bot) {
        this.bot = bot;
    }

    void sendMessage(long chatId, Integer threadId, String text) {
        SendMessage message = new SendMessage(Long.toString(chatId), text);
        if (threadId != null) {
            message.setMessageThreadId(threadId);
        }
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    void sendChatTyping(long chatId, Integer threadId) {
        SendChatAction action = new SendChatAction();
        action.setChatId(Long.toString(chatId));
        action.setAction(ActionType.TYPING);
        if (threadId != null) {
            action.setMessageThreadId(threadId);
        }
        try {
            bot.execute(action);
            log.info("Outputting typing action in chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send action to chat {}: {}", chatId, e.getMessage(), e);
        }
    }
}
