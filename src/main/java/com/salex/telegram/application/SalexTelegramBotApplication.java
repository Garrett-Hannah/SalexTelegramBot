package com.salex.telegram.application;

import com.salex.telegram.telegram.SalexTelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Spring Boot application entry point that registers the Telegram bot once the context is ready.
 */
@SpringBootApplication(
        scanBasePackages = "com.salex.telegram",
        exclude = DataSourceAutoConfiguration.class
)
@ConfigurationPropertiesScan("com.salex.telegram.application.config")
public class SalexTelegramBotApplication {
    private static final Logger log = LoggerFactory.getLogger(SalexTelegramBotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SalexTelegramBotApplication.class, args);
    }

    @Bean
    CommandLineRunner registerBot(TelegramBotsApi telegramBotsApi, SalexTelegramBot bot) {
        return args -> {
            try {
                telegramBotsApi.registerBot(bot);
                log.info("Bot registered with username {}", bot.getBotUsername());
            } catch (TelegramApiException ex) {
                log.error("Failed to register Telegram bot", ex);
                throw ex;
            }
        };
    }
}
