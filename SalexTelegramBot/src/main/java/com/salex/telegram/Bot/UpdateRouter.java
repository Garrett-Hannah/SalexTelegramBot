package com.salex.telegram.Bot;

import com.salex.telegram.Users.UserRecord;
import com.salex.telegram.Users.UserService;
import com.salex.telegram.modules.CommandHandler;
import com.salex.telegram.modules.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

/**
 * Centralises routing of incoming updates to command handlers or modules.
 */
final class UpdateRouter {
    private static final Logger log = LoggerFactory.getLogger(UpdateRouter.class);

    private final ModuleRegistry moduleRegistry;
    private final Map<String, CommandHandler> commands;
    private final UserService userService;
    private final TelegramSender sender;

    UpdateRouter(ModuleRegistry moduleRegistry,
                 Map<String, CommandHandler> commands,
                 UserService userService,
                 TelegramSender sender) {
        this.moduleRegistry = moduleRegistry;
        this.commands = commands;
        this.userService = userService;
        this.sender = sender;
    }

    void route(Update update, SalexTelegramBot bot) {
        if (update == null || !update.hasMessage()) {
            log.debug("Ignored update without message content");
            return;
        }

        Message message = update.getMessage();
        long chatId = message.getChatId();
        long telegramUserId = message.getFrom() != null ? message.getFrom().getId() : -1L;
        Integer threadId = message.getMessageThreadId();
        String text = message.hasText() ? message.getText().trim() : "";

        log.info("Received update{} in chat {} from user {}",
                text.isEmpty() ? "" : (" \"" + text + "\""),
                chatId,
                telegramUserId);

        User telegramUser = message.getFrom();
        if (telegramUser == null) {
            log.warn("Received message in chat {} without sender metadata; update ignored", chatId);
            return;
        }

        long userId;
        try {
            UserRecord userRecord = userService.ensureUser(telegramUser);
            userId = userRecord.id();
        } catch (SQLException ex) {
            sender.sendMessage(chatId, threadId, "[Error] Failed to resolve user: " + ex.getMessage());
            log.error("Failed to resolve user for chat {}: {}", chatId, ex.getMessage(), ex);
            return;
        }

        if (!text.isEmpty() && text.startsWith("/")) {
            dispatchCommand(update, bot, userId, chatId, threadId);
            return;
        }

        moduleRegistry.stream()
                .filter(module -> module.canHandle(update, userId))
                .findFirst()
                .ifPresentOrElse(module -> {
                    log.debug("Routing update in chat {} to module {}", chatId, module.getClass().getSimpleName());
                    module.handle(update, bot, userId);
                }, () -> log.debug("No module accepted update in chat {} from user {}", chatId, userId));
    }

    private void dispatchCommand(Update update,
                                 SalexTelegramBot bot,
                                 long userId,
                                 long chatId,
                                 Integer threadId) {
        String commandText = update.getMessage().getText().trim();
        String commandKey = commandText.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        CommandHandler handler = commands.get(commandKey);
        if (handler == null) {
            sender.sendMessage(chatId, threadId, "Unknown command: " + commandKey);
            log.warn("User {} invoked unknown command {}", userId, commandKey);
            return;
        }

        log.info("Executing command {} for user {}", handler.getName(), userId);
        handler.handle(update, bot, userId);
    }
}
