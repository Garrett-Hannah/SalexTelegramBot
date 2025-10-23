package com.salex.telegram.user.infrastructure;

import com.salex.telegram.user.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.assertThat;
class InMemoryUserServiceTest {

    private InMemoryUserService service;
    private User telegramUser;

    @BeforeEach
    void setUp() {
        service = new InMemoryUserService();
        telegramUser = new User();
        telegramUser.setId(123L);
        telegramUser.setUserName("alice");
        telegramUser.setFirstName("Alice");
        telegramUser.setLastName("Doe");
    }

    @Test
    void ensureUserCachesRecordsPerTelegramId() throws Exception {
        UserRecord first = service.ensureUser(telegramUser);
        UserRecord second = service.ensureUser(telegramUser);

        assertThat(first).isSameAs(second);
        assertThat(first.username()).isEqualTo("alice");
    }

    @Test
    void findByTelegramIdReturnsCachedRecord() throws Exception {
        service.ensureUser(telegramUser);

        assertThat(service.findByTelegramId(123L))
                .isPresent()
                .get()
                .extracting(UserRecord::telegramId)
                .isEqualTo(123L);
        assertThat(service.findByTelegramId(999L)).isEmpty();
    }
}
