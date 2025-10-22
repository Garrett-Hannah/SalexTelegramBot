package com.salex.telegram.config;

import com.salex.telegram.ai.ConversationContextService;
import com.salex.telegram.messaging.MessageRepository;
import com.salex.telegram.transcription.commands.TranscriptionMessageFormatter;
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
