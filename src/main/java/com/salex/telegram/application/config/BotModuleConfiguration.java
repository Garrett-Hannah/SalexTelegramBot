package com.salex.telegram.application.config;

import com.salex.telegram.conversation.ConversationContextService;
import com.salex.telegram.infrastructure.messaging.MessageRepository;
import com.salex.telegram.transcription.presentation.TranscriptionMessageFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
