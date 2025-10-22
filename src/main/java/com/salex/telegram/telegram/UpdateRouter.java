package com.salex.telegram.telegram;

import com.salex.telegram.application.modules.CommandHandler;
import com.salex.telegram.application.modules.ConversationalRelayService;
import com.salex.telegram.application.modules.MessagingHandlerService;
import com.salex.telegram.user.UserRecord;
import com.salex.telegram.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Centralises routing of incoming updates to command handlers or modules.
 */
//TODO: maybe set into a component/service?
final class UpdateRouter {
    private static final Logger log = LoggerFactory.getLogger(UpdateRouter.class);

    private final Map<String, CommandHandler> commands;
    private final UserService userService;
    private final TelegramSender sender;
    private final List<MessagingHandlerService> messagingHandlerServices;

    UpdateRouter(Map<String, CommandHandler> commands,
                 UserService userService,
                 TelegramSender sender,
                 List<MessagingHandlerService> handlerServiceList) {
        this.commands = commands;
        this.userService = userService;
        this.sender = sender;
        this.messagingHandlerServices = handlerServiceList;
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

        if (text.startsWith("/")) {
            dispatchCommand(update, bot, userId, chatId, threadId);
            return;
        }


        //TODO: make handler list into its own class allowing
        //For higher prieirt of the stuff.
        for(MessagingHandlerService handler : messagingHandlerServices){
            if(handler.canHandle(update, userId))
            {
                handler.handle(update, bot, userId);
                return;
            }
        }

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
