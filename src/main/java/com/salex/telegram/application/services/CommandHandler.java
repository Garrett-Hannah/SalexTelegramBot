package com.salex.telegram.application.services;

import com.salex.telegram.telegram.SalexTelegramBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Contract for handling Telegram bot commands.
 */
public interface CommandHandler {
    /**
     * Returns the canonical command trigger (for example {@code /menu}).
     *
     * @return the command keyword recognised by the bot
     */
    String getName();

    /**
     * Describes the behaviour of the command for help menus.
     *
     * @return a human-readable description of the command
     */
    String getDescription();

    /**
     * Processes an incoming update that triggered the command.
     *
     * @param update the Telegram update payload
     * @param bot    the bot instance used for responses
     * @param userId the internal user identifier resolved for the update
     */
    void handle(Update update, SalexTelegramBot bot, long userId);
}
