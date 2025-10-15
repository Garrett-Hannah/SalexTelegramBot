package com.salex.telegram.Bot;

import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;

public interface TelegramBotModule {
    boolean canHandle(Update update);
    void handle(Update update, SalexTelegramBot bot, long userID);
    Map<String, CommandHandler> getCommands();
}
