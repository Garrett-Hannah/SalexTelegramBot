package com.salex.telegram.user;

import org.telegram.telegrambots.meta.api.objects.User;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Resolves Telegram users to the bot's internal user records, shielding callers from persistence details.
 */
public interface UserService {

    /**
     * Ensures that a Telegram user has a corresponding internal record, creating or updating it if necessary.
     *
     * @param telegramUser Telegram user metadata received from an update
     * @return the persisted user record
     * @throws SQLException when persistence operations fail
     */
    UserRecord ensureUser(User telegramUser) throws SQLException;

    /**
     * Looks up a user record by Telegram identifier.
     *
     * @param telegramId telegram user identifier
     * @return optional user record if it exists
     * @throws SQLException when persistence operations fail
     */
    Optional<UserRecord> findByTelegramId(long telegramId) throws SQLException;
}
