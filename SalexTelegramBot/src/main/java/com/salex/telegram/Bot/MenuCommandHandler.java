package com.salex.telegram.Bot;

import com.salex.telegram.commanding.CommandHandler;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;

public class MenuCommandHandler implements CommandHandler {
    private final Map<String, CommandHandler> commandHandlers;

    public MenuCommandHandler(Map<String, CommandHandler> commandHandlers) {
        this.commandHandlers = commandHandlers;
    }

    @Override
    public String getName() {
        return "/menu";
    }

    @Override
    public String getDescription() {
        return "Show the available bot commands.";
    }

    @Override
    public void handle(Update update, TelegramBot bot, long userId) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        long chatId = update.getMessage().getChatId();
        StringBuilder builder = new StringBuilder("Available commands:\n");
        commandHandlers.values().stream()
                .filter(handler -> handler != this)
                .forEach(handler -> builder.append(handler.getName())
                        .append(" - ")
                        .append(handler.getDescription())
                        .append(System.lineSeparator()));

        bot.sendMessage(chatId, builder.toString().trim());
    }
}
