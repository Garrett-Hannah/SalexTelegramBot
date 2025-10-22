package com.salex.telegram.application.config;

import com.salex.telegram.application.config.condition.JdbcConnectionAvailableCondition;
import com.salex.telegram.infrastructure.database.ConnectionFactory;
import com.salex.telegram.infrastructure.database.ConnectionProvider;
import com.salex.telegram.infrastructure.database.RefreshingConnectionProvider;
import com.salex.telegram.telegram.SalexTelegramBot;
import com.salex.telegram.transcription.infrastructure.TelegramAudioDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Configures Telegram infrastructure beans such as the bots API and optional JDBC connection provider.
 */
@Configuration
@EnableConfigurationProperties({TelegramBotProperties.class, DatabaseConfiguration.class})
public class TelegramBotConfiguration {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotConfiguration.class);

    @Bean
    TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    TelegramAudioDownloader telegramAudioDownloader(@Lazy SalexTelegramBot salexTelegramBot) {
        return new TelegramAudioDownloader(salexTelegramBot);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "bot.database", name = "jdbc-url")
    @Conditional(JdbcConnectionAvailableCondition.class)
    ConnectionProvider connectionProvider(DatabaseConfiguration properties) {
        return createConnectionProvider(properties);
    }

    private ConnectionProvider createConnectionProvider(DatabaseConfiguration properties) {
        String url = properties.jdbcUrl().orElseThrow(() ->
                new IllegalStateException("bot.database.jdbc-url must be provided when the connection provider bean is created"));
        ConnectionFactory factory = () -> DriverManager.getConnection(
                url,
                properties.getUsername(),
                properties.getPassword()
        );
        RefreshingConnectionProvider provider = new RefreshingConnectionProvider(
                factory,
                properties.getValidationTimeoutSeconds()
        );
        try {
            provider.getConnection();
            log.info("Database connection established for bot startup");
        } catch (SQLException ex) {
            log.warn("Database connection validation failed after initial check: {}", ex.getMessage());
        }
        return provider;
    }


}
