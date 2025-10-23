package com.salex.telegram.telegram;

import com.salex.telegram.application.config.TelegramBotProperties;
import com.salex.telegram.application.services.UpdateHandlingService;
import com.salex.telegram.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Spring-managed Telegram bot that routes updates through to modules or senders.
 */
@Component
public class SalexTelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(SalexTelegramBot.class);

    private final String username;

    //TODO: turn command into a component to interact with the spring project.
    private final TelegramSender telegramSender;
    private final UpdateRouter updateRouter;
    private final CommandRouter commandRouter;

    //Need to find if i can set up a alternate way of this. maybe maybe not idk,
    public SalexTelegramBot(TelegramBotProperties properties,
                            CommandRegistry commandRegistry,
                            UserService userService,
                            List<UpdateHandlingService> messagingHandlerServiceList) {
        super(properties.getToken());
        this.username = properties.getUsername();
        this.telegramSender = new TelegramSender(this);
        this.commandRouter = new CommandRouter(commandRegistry, telegramSender);
        this.updateRouter = new UpdateRouter(commandRouter, userService, telegramSender, messagingHandlerServiceList);

        log.info("TelegramBot registered {} command handlers", commandRegistry.asMap().size());
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateRouter.route(update, this);
    }

    public TelegramSender getTelegramSender() {
        return telegramSender;
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, null, text);
    }

    //TODO: also change this. I think it could maybe be within a better defined
    //Chat component rather than this message function.
    public void sendMessage(long chatId, Integer threadId, String text) {
        telegramSender.sendMessage(chatId, threadId, text);
    }

    //TODO: optional, maybe change where stuff like this goes.
    public void sendChatTyping(long chatId, Integer threadId) {
        telegramSender.sendChatTyping(chatId, threadId);
    }
}
