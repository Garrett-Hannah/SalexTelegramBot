package com.salex.telegram.application.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Holds Telegram bot credentials sourced from configuration.
 */
@Validated
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    @NotBlank
    private String token;

    @NotBlank
    private String username;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
