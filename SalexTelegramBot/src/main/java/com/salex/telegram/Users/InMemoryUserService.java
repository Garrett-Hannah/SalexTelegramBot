package com.salex.telegram.Users;

import org.telegram.telegrambots.meta.api.objects.User;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight user service that uses Telegram identifiers directly without persistence.
 */
public class InMemoryUserService implements UserService {
    private final Map<Long, UserRecord> cache = new ConcurrentHashMap<>();

    @Override
    public UserRecord ensureUser(User telegramUser) throws SQLException {
        Objects.requireNonNull(telegramUser, "telegramUser");
        return cache.computeIfAbsent(telegramUser.getId(),
                id -> UserRecord.fromTelegram(id, telegramUser));
    }

    @Override
    public Optional<UserRecord> findByTelegramId(long telegramId) throws SQLException {
        return Optional.ofNullable(cache.get(telegramId));
    }
}
