package com.salex.telegram.telegram;

import com.salex.telegram.application.services.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Indexes {@link CommandHandler} instances by their command trigger and provides lookup helpers.
 * Command names are normalised to lower-case for consistent matching regardless of user input.
 */
@Component
public class CommandRegistry {
    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, CommandHandler> handlers;

    public CommandRegistry(List<CommandHandler> commandHandlers) {
        Map<String, CommandHandler> index = new LinkedHashMap<>();
        for (CommandHandler handler : commandHandlers) {
            if (handler == null) {
                continue;
            }
            String commandName = handler.getName();
            String key = normalize(commandName);
            if (key == null) {
                log.warn("Skipping command handler {} because it exposes an empty name", handler.getClass().getName());
                continue;
            }
            CommandHandler previous = index.putIfAbsent(key, handler);
            if (previous != null) {
                log.warn("Duplicate command name '{}' found for {} and {}; keeping the first instance",
                        commandName, previous.getClass().getName(), handler.getClass().getName());
            }
        }
        this.handlers = Collections.unmodifiableMap(index);
        log.info("Registered {} command handler(s): {}", this.handlers.size(), this.handlers.keySet());
    }

    /**
     * Locates the handler responsible for the given command trigger.
     *
     * @param command incoming command text (for example {@code /menu})
     * @return optional containing the matching handler
     */
    public Optional<CommandHandler> find(String command) {
        if (command == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(normalize(command)));
    }

    /**
     * Exposes all registered command handlers in the order they were discovered.
     *
     * @return immutable collection of handlers
     */
    public Collection<CommandHandler> handlers() {
        return handlers.values();
    }

    /**
     * Provides a read-only view of the command catalogue keyed by command name.
     *
     * @return immutable map of command name to handler
     */
    public Map<String, CommandHandler> asMap() {
        return handlers;
    }

    private String normalize(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
