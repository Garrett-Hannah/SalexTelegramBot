package com.salex.telegram.config;

import com.salex.telegram.ai.ChatCompletionClient;
import com.salex.telegram.bot.SalexTelegramBot;
import com.salex.telegram.database.ConnectionFactory;
import com.salex.telegram.database.ConnectionProvider;
import com.salex.telegram.database.RefreshingConnectionProvider;
import com.salex.telegram.messaging.MessageRepository;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.modules.ticketing.commands.TicketMessageFormatter;
import com.salex.telegram.transcription.TranscriptionService;
import com.salex.telegram.modules.transcription.commands.TranscriptionMessageFormatter;
import com.salex.telegram.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Configures the Telegram bot and its optional dependencies for the Spring context.
 */
@Configuration
@EnableConfigurationProperties({TelegramBotProperties.class, DatabaseProperties.class})
public class TelegramBotConfiguration {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotConfiguration.class);

    @Bean
    TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    SalexTelegramBot salexTelegramBot(TelegramBotProperties botProperties,
                                      DatabaseProperties databaseProperties,
                                      ObjectProvider<TicketService> ticketServices,
                                      ObjectProvider<TicketMessageFormatter> ticketFormatters,
                                      ObjectProvider<TranscriptionService> transcriptionServices,
                                      ObjectProvider<TranscriptionMessageFormatter> transcriptionFormatters,
                                      ObjectProvider<MessageRepository> messageRepositories,
                                      ObjectProvider<ChatCompletionClient> chatCompletionClients,
                                      ObjectProvider<UserService> userServices) {
        ConnectionProvider connectionProvider = createConnectionProvider(databaseProperties);

        return new SalexTelegramBot(
                botProperties.getToken(),
                botProperties.getUsername(),
                connectionProvider,
                ticketServices.getIfAvailable(),
                ticketFormatters.getIfAvailable(),
                transcriptionServices.getIfAvailable(),
                transcriptionFormatters.getIfAvailable(),
                messageRepositories.getIfAvailable(),
                chatCompletionClients.getIfAvailable(),
                userServices.getIfAvailable()
        );
    }

    private ConnectionProvider createConnectionProvider(DatabaseProperties properties) {
        return properties.jdbcUrl()
                .map(url -> {
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
                        return provider;
                    } catch (SQLException ex) {
                        log.warn("Failed to establish database connection; bot will run with in-memory storage: {}", ex.getMessage());
                        provider.close();
                        return null;
                    }
                })
                .orElseGet(() -> {
                    log.warn("JDBC URL not provided; bot will run with in-memory storage");
                    return null;
                });
    }
}
