package com.salex.telegram.users;

import org.telegram.telegrambots.meta.api.objects.User;

/**
 * Snapshot of a Telegram user that has been synchronised with the bot's persistence layer.
 */
public record UserRecord(long id,
                         long telegramId,
                         String username,
                         String firstName,
                         String lastName) {

    public static UserRecord fromTelegram(long id, User telegramUser) {
        if (telegramUser == null) {
            throw new IllegalArgumentException("telegramUser must not be null");
        }
        return new UserRecord(
                id,
                telegramUser.getId(),
                telegramUser.getUserName(),
                telegramUser.getFirstName(),
                telegramUser.getLastName()
        );
    }
}
