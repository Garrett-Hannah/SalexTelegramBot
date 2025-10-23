package com.salex.telegram.application.services;

import com.salex.telegram.telegram.CommandRegistry;
import com.salex.telegram.telegram.SalexTelegramBot;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

//Question: does this need to be a service? maybe, as it needs to have access to the command registry.
/**
 * Presents the list of available commands registered with the bot.
 */
public class MenuCommandHandler implements CommandHandler {

    //This creates circular dependency.
    private final CommandRegistry commandRegistry;

    /**
     * Creates a handler that renders a help menu based on the given commands.
     *
     * @param commandRegistry registry exposing available commands
     */
    public MenuCommandHandler(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
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
    public void handle(Update update, SalexTelegramBot bot, long userId) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        long chatId = update.getMessage().getChatId();
        Integer threadId = update.getMessage().getMessageThreadId();
        StringBuilder builder = new StringBuilder("Available commands:\n");
        commandRegistry.handlers().stream()
                .filter(handler -> handler != this)
                .forEach(handler -> builder.append(handler.getName())
                        .append(" - ")
                        .append(handler.getDescription())
                        .append(System.lineSeparator()));

        bot.sendMessage(chatId, threadId, builder.toString().trim());
    }
}
