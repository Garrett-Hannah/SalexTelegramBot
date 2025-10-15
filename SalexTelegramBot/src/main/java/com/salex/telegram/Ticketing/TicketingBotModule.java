package com.salex.telegram.Ticketing;

import com.salex.telegram.Bot.CommandHandler;
import com.salex.telegram.Bot.SalexTelegramBot;
import com.salex.telegram.Bot.TelegramBotModule;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;

public class TicketingBotModule implements TelegramBotModule {
    @Override
    public boolean canHandle (Update update) {
        return false;
    }

    @Override
    public void handle (Update update, SalexTelegramBot bot, long userID) {

    }

    @Override
    public Map<String, CommandHandler> getCommands () {
        return Map.of();
    }
}
