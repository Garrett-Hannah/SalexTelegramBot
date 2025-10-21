package com.salex.telegram.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Maintains the ordered collection of {@link TelegramBotModule TelegramBotModules} registered with the bot.
 * Provides lookup facilities by module type while preserving deterministic dispatch order.
 */
public final class ModuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModuleRegistry.class);

    private final List<TelegramBotModule> orderedModules = new ArrayList<>();
    private final Map<Class<? extends TelegramBotModule>, TelegramBotModule> moduleByType = new IdentityHashMap<>();

    /**
     * Registers a module instance. The first module added for a given class wins; subsequent registrations of the
     * same class are ignored and logged at WARN level.
     *
     * @param module module instance to register
     */
    public void register(TelegramBotModule module) {
        Objects.requireNonNull(module, "module");
        Class<? extends TelegramBotModule> type = module.getClass();
        if (moduleByType.containsKey(type)) {
            log.warn("Module {} already registered; ignoring duplicate instance {}", type.getSimpleName(), module);
            return;
        }
        orderedModules.add(module);
        moduleByType.put(type, module);
    }

    /**
     * Checks whether a module of the requested type has been registered.
     */
    public boolean contains(Class<? extends TelegramBotModule> moduleType) {
        Objects.requireNonNull(moduleType, "moduleType");
        return moduleByType.containsKey(moduleType);
    }

    /**
     * Attempts to retrieve a previously registered module by its concrete type.
     *
     * @param moduleType concrete module class
     * @param <T>        module subtype
     * @return optional module instance
     */
    public <T extends TelegramBotModule> Optional<T> get(Class<T> moduleType) {
        Objects.requireNonNull(moduleType, "moduleType");
        return Optional.ofNullable(moduleType.cast(moduleByType.get(moduleType)));
    }

    /**
     * @return immutable view of the registered modules in insertion order
     */
    public List<TelegramBotModule> orderedModules() {
        return Collections.unmodifiableList(orderedModules);
    }

    /**
     * Applies the supplied consumer to each registered module in order.
     */
    public void forEach(Consumer<TelegramBotModule> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        orderedModules.forEach(consumer);
    }

    /**
     * @return stream of modules in registration order
     */
    public Stream<TelegramBotModule> stream() {
        return orderedModules.stream();
    }

    /**
     * @return number of registered modules
     */
    public int size() {
        return orderedModules.size();
    }
}
