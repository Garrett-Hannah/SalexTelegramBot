package com.salex.telegram.application.config.condition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Spring condition that enables JDBC-backed components only when the configured database is reachable.
 */
public class JdbcConnectionAvailableCondition extends SpringBootCondition {
    private static final Logger log = LoggerFactory.getLogger(JdbcConnectionAvailableCondition.class);

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConditionMessage.Builder message = ConditionMessage.forCondition("JdbcConnectionAvailable");
        String jdbcUrl = context.getEnvironment().getProperty("bot.database.jdbc-url");
        if (!StringUtils.hasText(jdbcUrl)) {
            return ConditionOutcome.noMatch(message.because("bot.database.jdbc-url not set"));
        }

        //TODO: FIND A WAY TO DO THIS PROPER, for now default to env vars.
        //String username = context.getEnvironment().getProperty("bot.database.username");
        //String password = context.getEnvironment().getProperty("bot.database.password");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASS");

        int timeout = context.getEnvironment().getProperty("bot.database.validation-timeout-seconds", Integer.class, 2);
        if (timeout > 0) {
            DriverManager.setLoginTimeout(timeout);
        }
        try (Connection ignored = createConnection(jdbcUrl, username, password)) {
            return ConditionOutcome.match(message.available("Database reachable"));
        } catch (SQLException ex) {
            log.warn("Database unreachable at {}: {}", jdbcUrl, ex.getMessage());
            return ConditionOutcome.noMatch(message.because("Failed to connect: " + ex.getMessage()));
        }
    }

    private Connection createConnection(String jdbcUrl, String username, String password) throws SQLException {
        Properties properties = new Properties();
        if (StringUtils.hasText(username)) {
            properties.setProperty("user", username);
        }
        if (StringUtils.hasText(password)) {
            properties.setProperty("password", password);
        }

        if (properties.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, properties);
    }
}
