package com.salex.telegram.infrastructure.messaging;

import com.salex.telegram.infrastructure.database.ConnectionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the message repository, switching between JDBC-backed persistence and a no-op
 * implementation depending on whether a {@link ConnectionProvider} is available.
 */
@Configuration
public class JdbcMessageConfiguration {

    @Bean
    public MessageRepository messageRepository(ObjectProvider<ConnectionProvider> connectionProviders) {
        ConnectionProvider connectionProvider = connectionProviders.getIfAvailable();
        if (connectionProvider != null) {
            return new JdbcMessageRepository(connectionProvider);
        }
        return new NoopMessageRepository();
    }
}
