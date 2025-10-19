package com.salex.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

/**
 * Database configuration backing the optional JDBC connection used by the bot.
 */
@ConfigurationProperties(prefix = "bot.database")
public class DatabaseProperties {

    /**
     * JDBC connection string. When absent, in-memory storage is used.
     */
    private String jdbcUrl;

    private String username;

    private String password;

    /**
     * Timeout in seconds used when validating connections.
     */
    private int validationTimeoutSeconds = 2;

    public Optional<String> jdbcUrl() {
        return Optional.ofNullable(jdbcUrl).filter(value -> !value.isBlank());
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getValidationTimeoutSeconds() {
        return validationTimeoutSeconds;
    }

    public void setValidationTimeoutSeconds(int validationTimeoutSeconds) {
        this.validationTimeoutSeconds = validationTimeoutSeconds;
    }
}
