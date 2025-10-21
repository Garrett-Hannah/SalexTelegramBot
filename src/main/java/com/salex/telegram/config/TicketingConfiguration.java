package com.salex.telegram.config;

import com.salex.telegram.database.ConnectionProvider;
import com.salex.telegram.ticketing.TicketRepository;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.ticketing.TicketSessionManager;
import com.salex.telegram.ticketing.inmemory.InMemoryTicketRepository;
import com.salex.telegram.ticketing.inmemory.InMemoryTicketSessionManager;
import com.salex.telegram.ticketing.server.ServerTicketRepository;
import com.salex.telegram.ticketing.server.ServerTicketSessionManager;
import com.salex.telegram.modules.ticketing.commands.TicketMessageFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides ticketing services backed by JDBC when a connection provider is available,
 * otherwise falls back to in-memory storage for local runs.
 */
@Configuration
public class TicketingConfiguration {

    @Bean
    @ConditionalOnBean(ConnectionProvider.class)
    TicketRepository serverTicketRepository(ConnectionProvider connectionProvider) {
        return new ServerTicketRepository(connectionProvider);
    }

    @Bean
    @ConditionalOnMissingBean(TicketRepository.class)
    TicketRepository inMemoryTicketRepository() {
        return new InMemoryTicketRepository();
    }

    @Bean
    @ConditionalOnBean(ConnectionProvider.class)
    TicketSessionManager serverTicketSessionManager(ConnectionProvider connectionProvider) {
        return new ServerTicketSessionManager(connectionProvider);
    }

    @Bean
    @ConditionalOnMissingBean(TicketSessionManager.class)
    TicketSessionManager inMemoryTicketSessionManager() {
        return new InMemoryTicketSessionManager();
    }

    @Bean
    TicketService ticketService(TicketRepository repository, TicketSessionManager sessionManager) {
        return new TicketService(repository, sessionManager);
    }

    @Bean
    TicketMessageFormatter ticketMessageFormatter() {
        return new TicketMessageFormatter();
    }
}
