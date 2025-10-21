package com.salex.telegram.bot;

import com.salex.telegram.config.TelegramBotProperties;
import com.salex.telegram.modules.CommandHandler;
import com.salex.telegram.modules.ModuleRegistry;
import com.salex.telegram.modules.TelegramBotModule;
import com.salex.telegram.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;
import java.util.Optional;

/**
 * Spring-managed Telegram bot that routes updates through registered modules.
 */
@Component
public class SalexTelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(SalexTelegramBot.class);

    private final String username;
    private final ModuleRegistry moduleRegistry;
    private final Map<String, CommandHandler> commands;
    private final UserService userService;
    private final TelegramSender telegramSender;
    private final UpdateRouter updateRouter;

    public SalexTelegramBot(TelegramBotProperties properties,
                            ModuleRegistry moduleRegistry,
                            Map<String, CommandHandler> commands,
                            UserService userService) {
        super(properties.getToken());
        this.username = properties.getUsername();
        this.moduleRegistry = moduleRegistry;
        this.commands = commands;
        this.userService = userService;
        this.telegramSender = new TelegramSender(this);
        this.updateRouter = new UpdateRouter(moduleRegistry, commands, userService, telegramSender);

        log.info("Initialised {} modules", moduleRegistry.size());
        log.info("TelegramBot registered {} command handlers", commands.size());
    }

    /**
     * Checks whether the bot has an active module of the requested type.
     */
    public boolean hasModule(Class<? extends TelegramBotModule> moduleType) {
        return moduleRegistry.contains(moduleType);
    }

    /**
     * Retrieves a module instance by its concrete type.
     */
    public <T extends TelegramBotModule> Optional<T> getModule(Class<T> moduleType) {
        return moduleRegistry.get(moduleType);
    }

    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateRouter.route(update, this);
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, null, text);
    }

    public void sendMessage(long chatId, Integer threadId, String text) {
        telegramSender.sendMessage(chatId, threadId, text);
    }

    public void sendChatTyping(long chatId, Integer threadId) {
        telegramSender.sendChatTyping(chatId, threadId);
    }

    public void sendChatTyping(long chatId) {
        sendChatTyping(chatId, null);
    }
}
