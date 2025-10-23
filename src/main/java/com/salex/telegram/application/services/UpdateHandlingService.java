package com.salex.telegram.application.services;

import com.salex.telegram.telegram.SalexTelegramBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Represents a cohesive feature area of the bot. Services can expose command handlers
 * and optionally consume non-command updates when {@link #canHandle(Update, long)} returns true.
 */
public interface UpdateHandlingService {
    /**
     * Determines whether the module wants to handle the current update.
     *
     * @param update the raw Telegram update
     * @param userId the internal user identifier resolved by the bot
     * @return {@code true} if the module should process the update, {@code false} otherwise
     */
    boolean canHandle(Update update, long userId);

    /**
     * Processes an update previously accepted by {@link #canHandle(Update, long)}.
     *
     * @param update the Telegram update to process
     * @param bot    reference to the bot orchestrator for sending replies/actions
     * @param userId the internal user identifier resolved by the bot
     */
    void handle(Update update, SalexTelegramBot bot, long userId);
}
