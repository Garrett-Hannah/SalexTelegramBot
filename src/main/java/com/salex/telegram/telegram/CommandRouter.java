package com.salex.telegram.telegram;

import com.salex.telegram.application.services.CommandHandler;
import com.salex.telegram.application.services.MenuCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Routes slash commands to their corresponding {@link CommandHandler}.
 */
final class CommandRouter {
    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);

    private final CommandRegistry registry;
    private final TelegramSender sender;

    //Since giving it a registry would be circular we just set it to the
    //Side.
    private final MenuCommandHandler menuCommandHandler;

    CommandRouter(CommandRegistry registry, TelegramSender sender) {
        this.registry = registry;
        this.sender = sender;
        this.menuCommandHandler = new MenuCommandHandler(registry);

    }


    /**
     * Attempts to dispatch the command contained in the update.
     *
     * @param update telegram update carrying the command text
     * @param bot    bot instance used for downstream handling
     * @param userId resolved user identifier
     */
    void dispatch(Update update, SalexTelegramBot bot, long userId) {
        if (update == null || !update.hasMessage()) {
            return;
        }
        Message message = update.getMessage();
        if (!message.hasText()) {
            return;
        }

        long chatId = message.getChatId();
        Integer threadId = message.getMessageThreadId();
        String text = message.getText().trim();
        if (text.isEmpty() || !text.startsWith("/")) {
            return;
        }

        String commandToken = text.split("\\s+", 2)[0];

        if(Objects.equals(commandToken, menuCommandHandler.getName()))
        {
            menuCommandHandler.handle(update, bot, userId);
            return;
        }

        Optional<CommandHandler> handler = registry.find(commandToken);
        if (handler.isEmpty()) {
            sender.sendMessage(chatId, threadId, "Unknown command: " + commandToken.toLowerCase(Locale.ROOT));
            log.warn("User {} invoked unknown command {}", userId, commandToken);
            return;
        }


        CommandHandler commandHandler = handler.get();
        log.info("Executing command {} for user {}", commandHandler.getName(), userId);
        commandHandler.handle(update, bot, userId);
    }
}
