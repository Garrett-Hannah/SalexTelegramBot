package com.salex.telegram.Bot;

import com.salex.telegram.commanding.CommandHandler;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;

/**
 * Presents the list of available commands registered with the bot.
 */
public class MenuCommandHandler implements CommandHandler {
    private final Map<String, CommandHandler> commandHandlers;

    /**
     * Creates a handler that renders a help menu based on the given commands.
     *
     * @param commandHandlers mapping of command keywords to their handlers
     */
    public MenuCommandHandler(Map<String, CommandHandler> commandHandlers) {
        this.commandHandlers = commandHandlers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "/menu";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Show the available bot commands.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(Update update, SalexTelegramBot bot, long userId) {
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
