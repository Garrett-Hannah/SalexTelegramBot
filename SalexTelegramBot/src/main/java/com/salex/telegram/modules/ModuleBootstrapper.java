package com.salex.telegram.modules;

import com.salex.telegram.AiPackage.ChatCompletionClient;
import com.salex.telegram.AiPackage.ConversationContextService;
import com.salex.telegram.Messaging.MessageRepository;

import com.salex.telegram.Transcription.TranscriptionService;
import com.salex.telegram.Transcription.commands.TranscriptionMessageFormatter;
import com.salex.telegram.Ticketing.TicketService;

import com.salex.telegram.Ticketing.commands.TicketMessageFormatter;
import com.salex.telegram.modules.ticketing.TicketingBotModule;
import com.salex.telegram.modules.transcription.TranscriptionBotModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Creates and wires bot modules, returning a registry together with the aggregated command map.
 */
public final class ModuleBootstrapper {
    private static final Logger log = LoggerFactory.getLogger(ModuleBootstrapper.class);

    public ModuleBootstrapperResult bootstrap (TicketService ticketService,
                                               TicketMessageFormatter ticketFormatter,
                                               TranscriptionService transcriptionService,
                                               TranscriptionMessageFormatter transcriptionFormatter,
                                               MessageRepository messageRepository,
                                               ConversationContextService conversationContextService,
                                               ChatCompletionClient chatCompletionClient) {
        ModuleRegistry registry = new ModuleRegistry();
        if (ticketService != null && ticketFormatter != null) {
            registry.register(new TicketingBotModule(ticketService, ticketFormatter));
        }
        if (transcriptionService != null && transcriptionFormatter != null) {
            registry.register(new TranscriptionBotModule(transcriptionService, transcriptionFormatter));
        }
        registry.register(new ConversationalRelayModule(messageRepository, conversationContextService, chatCompletionClient));

        Map<String, CommandHandler> commandMap = new LinkedHashMap<>();
        registry.forEach(module -> module.getCommands().forEach((name, handler) -> {
            String key = name.toLowerCase(Locale.ROOT);
            CommandHandler previous = commandMap.put(key, handler);
            if (previous != null && previous != handler) {
                log.warn("Command {} supplied by {} overrides {}", key,
                        module.getClass().getSimpleName(), previous.getClass().getSimpleName());
            }
        }));
        Map<String, CommandHandler> commandsView = Collections.unmodifiableMap(commandMap);
        commandMap.put("/menu", new MenuCommandHandler(commandsView));
        return new ModuleBootstrapperResult(registry, commandsView);
    }
}
