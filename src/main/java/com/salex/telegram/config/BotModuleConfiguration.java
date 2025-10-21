package com.salex.telegram.config;

import com.salex.telegram.ai.ChatCompletionClient;
import com.salex.telegram.ai.ConversationContextService;
import com.salex.telegram.messaging.MessageRepository;
import com.salex.telegram.modules.CommandHandler;
import com.salex.telegram.modules.ModuleBootstrapperResult;
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
    TranscriptionMessageFormatter transcriptionMessageFormatter() {
        return new TranscriptionMessageFormatter();
    }
}
