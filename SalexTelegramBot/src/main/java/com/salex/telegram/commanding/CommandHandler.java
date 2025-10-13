package com.salex.telegram.commanding;

import com.salex.telegram.Bot.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface CommandHandler {
    String getName(); // eg /menu, /ticket etc...
    String getDescription();
    void handle(Update update, TelegramBot bot);
}
