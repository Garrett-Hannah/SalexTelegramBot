package com.salex.telegram.application.config;

import com.salex.telegram.infrastructure.database.ConnectionProvider;
import com.salex.telegram.user.UserService;
import com.salex.telegram.user.infrastructure.InMemoryUserService;
import com.salex.telegram.user.infrastructure.JdbcUserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a user service backed by JDBC when available, otherwise an in-memory variant.
 */
@Configuration
public class UserConfiguration {

    @Bean
    @ConditionalOnBean(ConnectionProvider.class)
    UserService jdbcUserService(ConnectionProvider connectionProvider) {
        return new JdbcUserService(connectionProvider);
    }

    @Bean
    @ConditionalOnMissingBean(UserService.class)
    UserService inMemoryUserService() {
        return new InMemoryUserService();
    }
}
