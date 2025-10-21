package com.salex.telegram.config;

import com.salex.telegram.ai.ChatCompletionClient;
import com.salex.telegram.ai.ConversationContextService;
import com.salex.telegram.messaging.MessageRepository;
import com.salex.telegram.modules.CommandHandler;
import com.salex.telegram.modules.ModuleBootstrapper;
import com.salex.telegram.modules.ModuleBootstrapperResult;
import com.salex.telegram.modules.ModuleRegistry;
import com.salex.telegram.modules.ticketing.commands.TicketMessageFormatter;
import com.salex.telegram.modules.transcription.commands.TranscriptionMessageFormatter;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.transcription.TranscriptionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Boots the registered bot modules and exposes the module registry and command map as beans.
 */
@Configuration
public class BotModuleConfiguration {

    @Bean
    ConversationContextService conversationContextService(MessageRepository messageRepository) {
        return new ConversationContextService(messageRepository);
    }

    @Bean
    ModuleBootstrapper moduleBootstrapper() {
        return new ModuleBootstrapper();
    }

    @Bean
    TranscriptionMessageFormatter transcriptionMessageFormatter() {
        return new TranscriptionMessageFormatter();
    }

    @Bean
    ModuleBootstrapperResult moduleBootstrapperResult(ModuleBootstrapper bootstrapper,
                                                      ObjectProvider<TicketService> ticketServices,
                                                      ObjectProvider<TicketMessageFormatter> ticketFormatters,
                                                      ObjectProvider<TranscriptionService> transcriptionServices,
                                                      ObjectProvider<TranscriptionMessageFormatter> transcriptionFormatters,
                                                      MessageRepository messageRepository,
                                                      ConversationContextService conversationContextService,
                                                      ChatCompletionClient chatCompletionClient) {
        return bootstrapper.bootstrap(
                ticketServices.getIfAvailable(),
                ticketFormatters.getIfAvailable(),
                transcriptionServices.getIfAvailable(),
                transcriptionFormatters.getIfAvailable(),
                messageRepository,
                conversationContextService,
                chatCompletionClient
        );
    }

    @Bean
    ModuleRegistry moduleRegistry(ModuleBootstrapperResult result) {
        return result.moduleRegistry();
    }

    @Bean
    Map<String, CommandHandler> commandHandlers(ModuleBootstrapperResult result) {
        return result.commands();
    }
}
